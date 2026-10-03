package com.tikitaka.document.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

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
import com.tikitaka.document.pdf.SelectedPdf;
import com.tikitaka.document.pdf.RevisionPdfComposer;
import com.tikitaka.document.repository.DocumentRevisionRepository;
import com.tikitaka.document.repository.RevisionPageRepository;
import com.tikitaka.document.repository.RevisionSlideRepository;
import com.tikitaka.document.repository.SlideRepository;
import com.tikitaka.document.storage.DocumentStorage;
import com.tikitaka.global.s3.S3ObjectNames;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
    private final DocumentAiProcessingService documentAiProcessingService;

    @Async
    public void completeAsync(UUID revisionId, String title) {
        List<String> uploadedKeys = new ArrayList<>();

        try {
            transactionTemplate.executeWithoutResult(
                    status -> complete(revisionId, title, uploadedKeys)
            );

        } catch (RuntimeException exception) {
            cleanup(uploadedKeys);

            transactionTemplate.executeWithoutResult(
                    status -> markFailed(revisionId)
            );

            log.error(
                    "Document revision completion failed. revisionId={}",
                    revisionId,
                    exception
            );
        }
    }

    private void complete(
            UUID revisionId,
            String title,
            List<String> uploadedKeys
    ) {
        DocumentRevision revision =
                revisionRepository.findByIdForUpdate(revisionId)
                        .orElse(null);

        if (revision == null
                || revision.getStatus() != RevisionStatus.PROCESSING) {
            return;
        }

        Document document = revision.getDocument();
        UUID documentId = document.getId();

        List<RevisionPage> pages =
                revisionPageRepository
                        .findAllByRevisionIdOrderByPositionAsc(revisionId);

        Map<UUID, Integer> originalPageNumbers = new HashMap<>();
        Map<UUID, Integer> sourcePageNumbers = new HashMap<>();

        for (RevisionPage page : pages) {
            if (page.getSourceType() == RevisionSourceType.ORIGINAL) {
                originalPageNumbers.put(
                        page.getOriginalSlide().getId(),
                        page.getOriginalSlide().getPageNumber()
                );
            } else {
                sourcePageNumbers.put(
                        page.getRevisionSlide().getId(),
                        page.getRevisionSlide().getSourcePageNumber()
                );
            }
        }

        long stageStarted = System.nanoTime();
        byte[] composedPdf =
                pdfComposer.compose(
                        storage.get(document.getPdfKey()),
                        revision.getSourcePdfKey() == null
                                ? null
                                : storage.get(revision.getSourcePdfKey()),
                        pages,
                        originalPageNumbers,
                        sourcePageNumbers
                );

        log.info("Revision PDF composition completed. revisionId={}, elapsedMs={}",
                revisionId, elapsedMillis(stageStarted));
        List<RevisionPage> outputPages = pages.stream()
                .filter(page -> page.getSourceType() != RevisionSourceType.REVISION
                        || page.getStatus() != RevisionPageStatus.DELETE_PENDING)
                .toList();
        Set<Integer> renderIndices = new HashSet<>();
        for (int index = 0; index < outputPages.size(); index++) {
            if (outputPages.get(index).getStatus() == RevisionPageStatus.DELETE_PENDING) {
                renderIndices.add(index);
            }
        }
        stageStarted = System.nanoTime();
        SelectedPdf processed = pdfProcessor.processSelected(composedPdf, renderIndices);
        log.info("Revision thumbnail processing completed. revisionId={}, pageCount={}, renderedPages={}, elapsedMs={}",
                revisionId, processed.pageCount(), renderIndices.size(), elapsedMillis(stageStarted));
        byte[] finalPdfBytes = composedPdf;
        stageStarted = System.nanoTime();

        String root =
                "documents/"
                        + documentId
                        + "/revisions/"
                        + revisionId
                        + "/completed/"
                        + UUID.randomUUID();

        String pdfKey = root + "/document.pdf";

        String documentThumbnailKey =
                root
                        + "/"
                        + S3ObjectNames.imageFilename(
                                title,
                                "썸네일",
                                ".png"
                        );

        put(
                uploadedKeys,
                pdfKey,
                finalPdfBytes,
                MediaType.APPLICATION_PDF_VALUE
        );

        Map<UUID, String> changedSlideThumbnailKeys =
                new HashMap<>();

        int outputIndex = 0;

        for (RevisionPage page : pages) {
            if (page.getSourceType() == RevisionSourceType.REVISION
                    && page.getStatus() == RevisionPageStatus.DELETE_PENDING) {
                continue;
            }

            if (page.getStatus() == RevisionPageStatus.DELETE_PENDING
                    || page.getSourceType() == RevisionSourceType.REVISION) {

                String key =
                        root
                                + "/slides/"
                                + S3ObjectNames.imageFilename(
                                        title,
                                        "슬라이드_" + (outputIndex + 1),
                                        ".png"
                                );

                if (page.getSourceType() == RevisionSourceType.REVISION) {
                    copy(uploadedKeys, page.getRevisionSlide().getThumbnailKey(), key);
                } else {
                    put(uploadedKeys, key, processed.pageThumbnails().get(outputIndex), MediaType.IMAGE_PNG_VALUE);
                }

                changedSlideThumbnailKeys.put(
                        page.getId(),
                        key
                );
            }

            outputIndex++;
        }

        RevisionPage firstPage = outputPages.get(0);
        String firstThumbnailKey = changedSlideThumbnailKeys.get(firstPage.getId());
        if (firstThumbnailKey == null) {
            firstThumbnailKey = firstPage.getOriginalSlide().getThumbnailKey();
        }
        // Keep a separate document thumbnail so future cleanup cannot delete a live slide image.
        copy(uploadedKeys, firstThumbnailKey, documentThumbnailKey);
        log.info("Revision storage writes completed. revisionId={}, elapsedMs={}", revisionId, elapsedMillis(stageStarted));

        List<String> obsoleteKeys =
                new ArrayList<>(
                        List.of(
                                document.getPdfKey(),
                                document.getThumbnailKey()
                        )
                );

        if (revision.getSourcePdfKey() != null) {
            obsoleteKeys.add(revision.getSourcePdfKey());
        }

        revisionSlideRepository
                .findAllByRevisionIdOrderBySourcePageNumberAsc(revisionId)
                .forEach(slide ->
                        obsoleteKeys.add(slide.getThumbnailKey())
                );

        List<Slide> existingSlides =
                slideRepository
                        .findAllByDocumentIdOrderByPageNumberAsc(documentId);

        existingSlides.forEach(slide ->
                slide.changePageNumber(
                        slide.getPageNumber() + 1000
                )
        );

        slideRepository.flush();

        List<Slide> insertedSlides = new ArrayList<>();
        int finalPageNumber = 0;

        for (RevisionPage page : pages) {
            if (page.getSourceType() == RevisionSourceType.REVISION
                    && page.getStatus() == RevisionPageStatus.DELETE_PENDING) {
                continue;
            }

            // Deleted revision pages are absent from the PDF, so compact final numbering.
            finalPageNumber++;
            if (page.getSourceType() == RevisionSourceType.ORIGINAL) {
                Slide slide = page.getOriginalSlide();

                slide.changePageNumber(finalPageNumber);

                if (page.getStatus() == RevisionPageStatus.DELETE_PENDING) {
                    obsoleteKeys.add(slide.getThumbnailKey());

                    slide.markPlaceholder(
                            changedSlideThumbnailKeys.get(page.getId())
                    );
                }

            } else {
                insertedSlides.add(
                        Slide.create(
                                document,
                                finalPageNumber,
                                changedSlideThumbnailKeys.get(page.getId())
                        )
                );
            }
        }

        slideRepository.saveAll(insertedSlides);

        document.replace(
                title,
                documentThumbnailKey,
                pdfKey,
                processed.pageCount()
        );

        revision.complete();

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        cleanup(obsoleteKeys);
                        reanalyzeCategories(documentId, finalPdfBytes);
                    }
                }
        );
    }

    private void reanalyzeCategories(
            UUID documentId,
            byte[] finalPdfBytes
    ) {
        try {
            documentAiProcessingService.process(
                    documentId,
                    finalPdfBytes
            );

        } catch (RuntimeException exception) {
            // Document revision 자체는 이미 정상 완료된 상태다.
            // Category AI 재분석 실패는 Document의 FAILED 상태로만 남긴다.
            log.warn(
                    "Document category reanalysis failed after revision completion. documentId={}",
                    documentId,
                    exception
            );
        }
    }

    private void markFailed(UUID revisionId) {
        revisionRepository.findByIdForUpdate(revisionId)
                .filter(revision ->
                        revision.getStatus() == RevisionStatus.PROCESSING
                )
                .ifPresent(DocumentRevision::fail);
    }

    private void put(
            List<String> uploadedKeys,
            String key,
            byte[] content,
            String contentType
    ) {
        storage.put(key, content, contentType);
        uploadedKeys.add(key);
    }

    private void copy(List<String> uploadedKeys, String sourceKey, String targetKey) {
        storage.copy(sourceKey, targetKey);
        uploadedKeys.add(targetKey);
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private void cleanup(List<String> keys) {
        keys.forEach(key -> {
            try {
                storage.delete(key);
            } catch (RuntimeException exception) {
                log.warn(
                        "Failed to clean up document storage object. key={}",
                        key,
                        exception
                );
            }
        });
    }
}
