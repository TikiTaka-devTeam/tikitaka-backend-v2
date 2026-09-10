package com.tikitaka.document.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.document.dto.response.DocumentCreateResponse;
import com.tikitaka.document.dto.response.DocumentDownloadResponse;
import com.tikitaka.document.dto.response.DocumentListItemResponse;
import com.tikitaka.document.dto.response.DocumentSlideResponse;
import com.tikitaka.document.dto.response.DocumentSlidesResponse;
import com.tikitaka.document.entity.Document;
import com.tikitaka.document.entity.Slide;
import com.tikitaka.document.exception.DocumentErrorCode;
import com.tikitaka.document.pdf.PdfProcessor;
import com.tikitaka.document.pdf.ProcessedPdf;
import com.tikitaka.document.repository.DocumentRepository;
import com.tikitaka.document.repository.SlideRepository;
import com.tikitaka.document.storage.DocumentStorage;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.s3.FileUploadType;
import com.tikitaka.global.s3.S3FileValidator;
import com.tikitaka.search.entity.RecentDocumentView;
import com.tikitaka.search.repository.RecentDocumentViewRepository;
import com.tikitaka.space.entity.PermissionType;
import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberRole;
import com.tikitaka.space.entity.SpaceMemberStatus;
import com.tikitaka.space.repository.SpaceMemberPermissionRepository;
import com.tikitaka.space.repository.SpaceMemberRepository;
import com.tikitaka.user.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final SlideRepository slideRepository;
    private final RecentDocumentViewRepository recentDocumentViewRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final SpaceMemberPermissionRepository permissionRepository;
    private final S3FileValidator fileValidator;
    private final PdfProcessor pdfProcessor;
    private final DocumentStorage storage;

    public List<DocumentListItemResponse> getDocuments(UUID spaceId, User currentUser) {
        requireApprovedMember(spaceId, currentUser);

        return documentRepository.findAllBySpaceIdOrderByCreatedAtDescIdDesc(spaceId)
                .stream()
                .map(document -> new DocumentListItemResponse(
                        document.getId(),
                        document.getTitle(),
                        storage.presignedGetUrl(document.getThumbnailKey()),
                        document.getPageCount(),
                        document.getCreatedAt()))
                .toList();
    }

    @Transactional
    public DocumentCreateResponse createDocument(
            UUID spaceId,
            String title,
            MultipartFile file,
            User currentUser
    ) {
        SpaceMember member = requireDocumentManager(spaceId, currentUser);
        String normalizedTitle = normalizeTitle(title);
        fileValidator.validate(file, FileUploadType.LECTURE_DOCUMENT);
        ProcessedPdf processed = pdfProcessor.process(file);

        String assetId = UUID.randomUUID().toString();
        String root = "documents/assets/" + assetId;
        String pdfKey = root + "/original.pdf";
        String documentThumbnailKey = root + "/thumbnail.png";
        List<String> uploadedKeys = new ArrayList<>();

        try {
            storage.put(pdfKey, processed.originalBytes(), MediaType.APPLICATION_PDF_VALUE);
            uploadedKeys.add(pdfKey);

            storage.put(documentThumbnailKey, processed.pageThumbnails().get(0), MediaType.IMAGE_PNG_VALUE);
            uploadedKeys.add(documentThumbnailKey);

            List<String> slideKeys = new ArrayList<>(processed.pageCount());
            for (int index = 0; index < processed.pageCount(); index++) {
                String slideKey = root + "/slides/" + (index + 1) + ".png";
                storage.put(slideKey, processed.pageThumbnails().get(index), MediaType.IMAGE_PNG_VALUE);
                uploadedKeys.add(slideKey);
                slideKeys.add(slideKey);
            }

            Document document = documentRepository.save(Document.create(
                    member.getSpace(),
                    normalizedTitle,
                    documentThumbnailKey,
                    pdfKey,
                    processed.pageCount()));

            List<Slide> slides = new ArrayList<>(processed.pageCount());
            for (int index = 0; index < processed.pageCount(); index++) {
                slides.add(Slide.create(document, index + 1, slideKeys.get(index)));
            }
            slideRepository.saveAll(slides);
            deleteAfterRollback(uploadedKeys);

            return new DocumentCreateResponse(
                    document.getId(),
                    document.getTitle(),
                    storage.presignedGetUrl(document.getThumbnailKey()),
                    document.getPageCount(),
                    document.getCreatedAt());
        } catch (RuntimeException exception) {
            cleanup(uploadedKeys, exception);
            throw exception;
        }
    }

    @Transactional
    public DocumentDownloadResponse downloadDocument(UUID documentId, User currentUser) {
        Document document = getDocument(documentId);
        requireApprovedMember(document.getSpace().getId(), currentUser);

        recentDocumentViewRepository.findByUserIdAndDocumentId(currentUser.getId(), documentId)
                .ifPresentOrElse(
                        RecentDocumentView::refreshViewedAt,
                        () -> recentDocumentViewRepository.save(
                                RecentDocumentView.create(currentUser, document)));

        return new DocumentDownloadResponse(storage.presignedGetUrl(document.getPdfKey()));
    }

    public DocumentSlidesResponse getSlides(UUID documentId, User currentUser) {
        Document document = getDocument(documentId);
        requireApprovedMember(document.getSpace().getId(), currentUser);

        List<DocumentSlideResponse> slides = slideRepository.findAllByDocumentIdOrderByPageNumberAsc(documentId)
                .stream()
                .map(slide -> new DocumentSlideResponse(
                        slide.getId(),
                        slide.getPageNumber(),
                        slide.getStatus()))
                .toList();

        return new DocumentSlidesResponse(
                documentId,
                storage.presignedGetUrl(document.getPdfKey()),
                document.getPageCount(),
                slides);
    }

    @Transactional
    public void deleteDocument(UUID documentId, User currentUser) {
        Document document = getDocument(documentId);
        requireDocumentManager(document.getSpace().getId(), currentUser);

        List<String> keys = new ArrayList<>();
        keys.add(document.getPdfKey());
        keys.add(document.getThumbnailKey());
        List<Slide> slides = slideRepository.findAllByDocumentIdOrderByPageNumberAsc(documentId);
        slides.stream()
                .map(Slide::getThumbnailKey)
                .forEach(keys::add);

        // Remove managed slides before their required document association is removed.
        slideRepository.deleteAll(slides);
        documentRepository.delete(document);
        deleteAfterCommit(keys);
    }

    private Document getDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(DocumentErrorCode.DOCUMENT_NOT_FOUND));
    }

    private SpaceMember requireApprovedMember(UUID spaceId, User currentUser) {
        return spaceMemberRepository
                .findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                        spaceId, currentUser.getId(), SpaceMemberStatus.APPROVED)
                .orElseThrow(() -> new BusinessException(DocumentErrorCode.DOCUMENT_ACCESS_DENIED));
    }

    private SpaceMember requireDocumentManager(UUID spaceId, User currentUser) {
        SpaceMember member = requireApprovedMember(spaceId, currentUser);
        if (member.getRole() == SpaceMemberRole.PROFESSOR) {
            return member;
        }
        if (member.getRole() == SpaceMemberRole.ASSISTANT
                && permissionRepository.existsBySpaceMemberIdAndPermission(
                        member.getId(), PermissionType.LECTURE_MATERIAL_MANAGE)) {
            return member;
        }
        throw new BusinessException(DocumentErrorCode.DOCUMENT_ACCESS_DENIED);
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            throw new BusinessException(DocumentErrorCode.INVALID_DOCUMENT_TITLE);
        }
        String normalized = title.trim();
        if (normalized.isEmpty() || normalized.length() > 255) {
            throw new BusinessException(DocumentErrorCode.INVALID_DOCUMENT_TITLE);
        }
        return normalized;
    }

    private void deleteAfterRollback(List<String> keys) {
        List<String> immutableKeys = List.copyOf(keys);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    cleanup(immutableKeys, null);
                }
            }
        });
    }

    private void deleteAfterCommit(List<String> keys) {
        List<String> immutableKeys = List.copyOf(keys);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cleanup(immutableKeys, null);
            }
        });
    }

    private void cleanup(List<String> keys, RuntimeException original) {
        for (String key : keys) {
            try {
                storage.delete(key);
            } catch (RuntimeException cleanupFailure) {
                if (original != null) {
                    original.addSuppressed(cleanupFailure);
                }
            }
        }
    }
}
