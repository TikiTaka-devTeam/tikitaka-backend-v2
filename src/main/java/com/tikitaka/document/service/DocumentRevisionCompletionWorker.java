package com.tikitaka.document.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.tikitaka.document.entity.Document;
import com.tikitaka.document.entity.DocumentRevision;
import com.tikitaka.document.entity.RevisionPage;
import com.tikitaka.document.entity.RevisionPageStatus;
import com.tikitaka.document.entity.RevisionSourceType;
import com.tikitaka.document.entity.RevisionStatus;
import com.tikitaka.document.entity.Slide;
import com.tikitaka.document.pdf.PdfProcessor;
import com.tikitaka.document.pdf.ProcessedPdf;
import com.tikitaka.document.pdf.RevisionPdfComposer;
import com.tikitaka.document.repository.DocumentRevisionRepository;
import com.tikitaka.document.repository.RevisionPageRepository;
import com.tikitaka.document.repository.RevisionSlideRepository;
import com.tikitaka.document.repository.SlideRepository;
import com.tikitaka.document.storage.DocumentStorage;
import com.tikitaka.global.s3.S3ObjectNames;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentRevisionCompletionWorker {
    private final TransactionTemplate transactionTemplate;
    private final DocumentRevisionRepository revisionRepository;
    private final RevisionPageRepository revisionPageRepository;
    private final RevisionSlideRepository revisionSlideRepository;
    private final SlideRepository slideRepository;
    private final DocumentStorage storage;
    private final RevisionPdfComposer pdfComposer;
    private final PdfProcessor pdfProcessor;

    @Async
    public void completeAsync(UUID revisionId) {
        List<String> uploadedKeys = new ArrayList<>();
        try {
            transactionTemplate.executeWithoutResult(status -> complete(revisionId, uploadedKeys));
        } catch (RuntimeException exception) {
            cleanup(uploadedKeys);
            transactionTemplate.executeWithoutResult(status -> markFailed(revisionId));
        }
    }

    private void complete(UUID revisionId, List<String> uploadedKeys) {
        DocumentRevision revision = revisionRepository.findByIdForUpdate(revisionId).orElse(null);
        if (revision == null || revision.getStatus() != RevisionStatus.PROCESSING) {
            return;
        }

        Document document = revision.getDocument();
        List<RevisionPage> pages = revisionPageRepository.findAllByRevisionIdOrderByPositionAsc(revisionId);
        Map<UUID, Integer> originalPageNumbers = new HashMap<>();
        Map<UUID, Integer> sourcePageNumbers = new HashMap<>();
        for (RevisionPage page : pages) {
            if (page.getSourceType() == RevisionSourceType.ORIGINAL) {
                originalPageNumbers.put(page.getOriginalSlide().getId(), page.getOriginalSlide().getPageNumber());
            } else {
                sourcePageNumbers.put(page.getRevisionSlide().getId(), page.getRevisionSlide().getSourcePageNumber());
            }
        }

        byte[] composedPdf = pdfComposer.compose(
                storage.get(document.getPdfKey()),
                revision.getSourcePdfKey() == null ? null : storage.get(revision.getSourcePdfKey()),
                pages,
                originalPageNumbers,
                sourcePageNumbers);
        ProcessedPdf processed = pdfProcessor.process(composedPdf);
        String root = "documents/" + document.getId() + "/revisions/" + revisionId + "/completed/" + UUID.randomUUID();
        String pdfKey = root + "/document.pdf";
        String documentThumbnailKey = root + "/" + S3ObjectNames.imageFilename(document.getTitle(), "썸네일", ".png");
        put(uploadedKeys, pdfKey, processed.originalBytes(), MediaType.APPLICATION_PDF_VALUE);
        put(uploadedKeys, documentThumbnailKey, processed.pageThumbnails().get(0), MediaType.IMAGE_PNG_VALUE);

        Map<UUID, String> changedSlideThumbnailKeys = new HashMap<>();
        int outputIndex = 0;
        for (RevisionPage page : pages) {
            if (page.getSourceType() == RevisionSourceType.REVISION
                    && page.getStatus() == RevisionPageStatus.DELETE_PENDING) {
                continue;
            }
            if (page.getStatus() == RevisionPageStatus.DELETE_PENDING
                    || page.getSourceType() == RevisionSourceType.REVISION) {
                String key = root + "/slides/" + S3ObjectNames.imageFilename(document.getTitle(), "슬라이드_" + (outputIndex + 1), ".png");
                put(uploadedKeys, key, processed.pageThumbnails().get(outputIndex), MediaType.IMAGE_PNG_VALUE);
                changedSlideThumbnailKeys.put(page.getId(), key);
            }
            outputIndex++;
        }

        List<String> obsoleteKeys = new ArrayList<>(List.of(document.getPdfKey(), document.getThumbnailKey()));
        if (revision.getSourcePdfKey() != null) {
            obsoleteKeys.add(revision.getSourcePdfKey());
        }
        revisionSlideRepository.findAllByRevisionIdOrderBySourcePageNumberAsc(revisionId)
                .forEach(slide -> obsoleteKeys.add(slide.getThumbnailKey()));
        List<Slide> existingSlides = slideRepository.findAllByDocumentIdOrderByPageNumberAsc(document.getId());
        existingSlides.forEach(slide -> slide.changePageNumber(slide.getPageNumber() + 1000));
        slideRepository.flush();

        List<Slide> insertedSlides = new ArrayList<>();
        for (RevisionPage page : pages) {
            if (page.getSourceType() == RevisionSourceType.REVISION
                    && page.getStatus() == RevisionPageStatus.DELETE_PENDING) {
                continue;
            }
            if (page.getSourceType() == RevisionSourceType.ORIGINAL) {
                Slide slide = page.getOriginalSlide();
                slide.changePageNumber(page.getPosition());
                if (page.getStatus() == RevisionPageStatus.DELETE_PENDING) {
                    obsoleteKeys.add(slide.getThumbnailKey());
                    slide.markPlaceholder(changedSlideThumbnailKeys.get(page.getId()));
                }
            } else {
                insertedSlides.add(Slide.create(document, page.getPosition(), changedSlideThumbnailKeys.get(page.getId())));
            }
        }
        slideRepository.saveAll(insertedSlides);
        document.replace(documentThumbnailKey, pdfKey, processed.pageCount());
        revision.complete();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cleanup(obsoleteKeys);
            }
        });
    }

    private void markFailed(UUID revisionId) {
        revisionRepository.findByIdForUpdate(revisionId)
                .filter(revision -> revision.getStatus() == RevisionStatus.PROCESSING)
                .ifPresent(DocumentRevision::fail);
    }

    private void put(List<String> uploadedKeys, String key, byte[] content, String contentType) {
        storage.put(key, content, contentType);
        uploadedKeys.add(key);
    }

    private void cleanup(List<String> keys) {
        keys.forEach(key -> {
            try {
                storage.delete(key);
            } catch (RuntimeException ignored) {
            }
        });
    }
}
