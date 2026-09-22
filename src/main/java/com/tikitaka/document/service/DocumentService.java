package com.tikitaka.document.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.document.dto.response.DocumentCreateResponse;
import com.tikitaka.document.dto.response.DocumentDownloadResponse;
import com.tikitaka.document.dto.response.DocumentListItemResponse;
import com.tikitaka.document.dto.response.DocumentSlideResponse;
import com.tikitaka.document.dto.response.DocumentSlidesResponse;
import com.tikitaka.document.dto.response.DocumentUpdateStatus;
import com.tikitaka.document.entity.Document;
import com.tikitaka.document.entity.RevisionStatus;
import com.tikitaka.document.entity.Slide;
import com.tikitaka.document.exception.DocumentErrorCode;
import com.tikitaka.document.pdf.PdfProcessor;
import com.tikitaka.document.pdf.ProcessedPdf;
import com.tikitaka.document.repository.DocumentRepository;
import com.tikitaka.document.repository.DocumentRevisionRepository;
import com.tikitaka.document.repository.SlideRepository;
import com.tikitaka.document.repository.projection.DocumentRevisionStatusProjection;
import com.tikitaka.document.storage.DocumentStorage;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.s3.FileUploadType;
import com.tikitaka.global.s3.S3FileValidator;
import com.tikitaka.global.s3.S3ObjectNames;
import com.tikitaka.notification.service.NotificationService;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentRevisionRepository documentRevisionRepository;
    private final SlideRepository slideRepository;
    private final RecentDocumentViewRepository recentDocumentViewRepository;

    private final SpaceMemberRepository spaceMemberRepository;
    private final SpaceMemberPermissionRepository permissionRepository;

    private final S3FileValidator fileValidator;
    private final PdfProcessor pdfProcessor;
    private final DocumentStorage storage;

    private final NotificationService notificationService;

    private final DocumentAiProcessingService documentAiProcessingService;

    public List<DocumentListItemResponse> getDocuments(
            UUID spaceId,
            User currentUser
    ) {
        requireApprovedMember(
                spaceId,
                currentUser
        );

        List<Document> documents = documentRepository
                .findAllBySpaceIdOrderByCreatedAtDescIdDesc(
                        spaceId
                );

        if (documents.isEmpty()) {
            return List.of();
        }

        Map<UUID, DocumentRevisionStatusProjection> latestStatuses =
                documentRevisionRepository
                        .findLatestCompletionStatuses(
                                documents.stream()
                                        .map(Document::getId)
                                        .toList()
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                DocumentRevisionStatusProjection::getDocumentId,
                                Function.identity()
                        ));

        return documents.stream()
                .map(document ->
                        documentListItem(
                                document,
                                latestStatuses.get(document.getId())
                        )
                )
                .toList();
    }

    private DocumentListItemResponse documentListItem(
            Document document,
            DocumentRevisionStatusProjection revision
    ) {
        return new DocumentListItemResponse(
                document.getId(),
                document.getTitle(),
                storage.presignedGetUrl(document.getThumbnailKey()),
                document.getPageCount(),
                document.getCreatedAt(),
                updateStatus(revision),
                revision == null ? null : revision.getRevisionId()
        );
    }

    private DocumentUpdateStatus updateStatus(
            DocumentRevisionStatusProjection revision
    ) {
        if (revision == null) {
            return DocumentUpdateStatus.ACTIVE;
        }

        return switch (RevisionStatus.valueOf(revision.getStatus())) {
            case PROCESSING -> DocumentUpdateStatus.PROCESSING;
            case FAILED -> DocumentUpdateStatus.FAILED;
            case COMPLETED -> DocumentUpdateStatus.ACTIVE;
            default -> throw new IllegalStateException(
                    "Unexpected completion status: " + revision.getStatus()
            );
        };
    }

    @Transactional
    public DocumentCreateResponse createDocument(
            UUID spaceId,
            String title,
            MultipartFile file,
            User currentUser
    ) {
        SpaceMember member =
                requireDocumentManager(
                        spaceId,
                        currentUser
                );

        String normalizedTitle =
                normalizeTitle(title);

        fileValidator.validate(
                file,
                FileUploadType.LECTURE_DOCUMENT
        );

        ProcessedPdf processed =
                pdfProcessor.process(file);

        String assetId =
                UUID.randomUUID()
                        .toString();

        String root =
                "documents/assets/"
                        + assetId;

        String pdfKey =
                root
                        + "/original.pdf";

        String documentThumbnailKey =
                root
                        + "/"
                        + S3ObjectNames.imageFilename(
                                normalizedTitle,
                                "썸네일",
                                ".png"
                        );

        List<String> uploadedKeys =
                new ArrayList<>();

        try {
            storage.put(
                    pdfKey,
                    processed.originalBytes(),
                    MediaType.APPLICATION_PDF_VALUE
            );

            uploadedKeys.add(
                    pdfKey
            );

            storage.put(
                    documentThumbnailKey,
                    processed
                            .pageThumbnails()
                            .get(0),
                    MediaType.IMAGE_PNG_VALUE
            );

            uploadedKeys.add(
                    documentThumbnailKey
            );

            List<String> slideKeys =
                    new ArrayList<>(
                            processed.pageCount()
                    );

            for (
                    int index = 0;
                    index < processed.pageCount();
                    index++
            ) {
                String slideKey =
                        root
                                + "/slides/"
                                + S3ObjectNames.imageFilename(
                                        normalizedTitle,
                                        "슬라이드_"
                                                + (index + 1),
                                        ".png"
                                );

                storage.put(
                        slideKey,
                        processed
                                .pageThumbnails()
                                .get(index),
                        MediaType.IMAGE_PNG_VALUE
                );

                uploadedKeys.add(
                        slideKey
                );

                slideKeys.add(
                        slideKey
                );
            }

            Document document =
                    documentRepository.save(
                            Document.create(
                                    member.getSpace(),
                                    normalizedTitle,
                                    documentThumbnailKey,
                                    pdfKey,
                                    processed.pageCount()
                            )
                    );

            List<Slide> slides =
                    new ArrayList<>(
                            processed.pageCount()
                    );

            for (
                    int index = 0;
                    index < processed.pageCount();
                    index++
            ) {
                slides.add(
                        Slide.create(
                                document,
                                index + 1,
                                slideKeys.get(index)
                        )
                );
            }

            slideRepository.saveAll(
                    slides
            );

            deleteAfterRollback(
                    uploadedKeys
            );

            registerDocumentAiProcessingAfterCommit(
                    document.getId(),
                    processed.originalBytes()
            );

            notificationService
                    .createDocumentUploadedNotification(
                            member.getSpace(),
                            document.getId()
                    );

            return new DocumentCreateResponse(
                    document.getId(),
                    document.getTitle(),
                    storage.presignedGetUrl(
                            document.getThumbnailKey()
                    ),
                    document.getPageCount(),
                    document.getCreatedAt()
            );

        } catch (RuntimeException exception) {

            cleanup(
                    uploadedKeys,
                    exception
            );

            throw exception;
        }
    }

    @Transactional
    public DocumentDownloadResponse downloadDocument(
            UUID documentId,
            User currentUser
    ) {
        Document document =
                getDocument(
                        documentId
                );

        requireApprovedMember(
                document.getSpace()
                        .getId(),
                currentUser
        );

        recentDocumentViewRepository
                .findByUserIdAndDocumentId(
                        currentUser.getId(),
                        documentId
                )
                .ifPresentOrElse(

                        RecentDocumentView::refreshViewedAt,

                        () ->
                                recentDocumentViewRepository.save(
                                        RecentDocumentView.create(
                                                currentUser,
                                                document
                                        )
                                )
                );

        return new DocumentDownloadResponse(
                storage.presignedGetUrl(
                        document.getPdfKey()
                )
        );
    }

    public DocumentSlidesResponse getSlides(
            UUID documentId,
            User currentUser
    ) {
        Document document =
                getDocument(
                        documentId
                );

        requireApprovedMember(
                document.getSpace()
                        .getId(),
                currentUser
        );

        List<DocumentSlideResponse> slides =
                slideRepository
                        .findAllByDocumentIdOrderByPageNumberAsc(
                                documentId
                        )
                        .stream()
                        .map(slide ->
                                new DocumentSlideResponse(
                                        slide.getId(),
                                        slide.getPageNumber(),
                                        slide.getStatus()
                                )
                        )
                        .toList();

        return new DocumentSlidesResponse(
                documentId,
                storage.presignedGetUrl(
                        document.getPdfKey()
                ),
                document.getPageCount(),
                slides
        );
    }

    @Transactional
    public void deleteDocument(
            UUID documentId,
            User currentUser
    ) {
        Document document =
                getDocument(
                        documentId
                );

        requireDocumentManager(
                document.getSpace()
                        .getId(),
                currentUser
        );

        List<String> keys =
                new ArrayList<>();

        keys.add(
                document.getPdfKey()
        );

        keys.add(
                document.getThumbnailKey()
        );

        List<Slide> slides =
                slideRepository
                        .findAllByDocumentIdOrderByPageNumberAsc(
                                documentId
                        );

        slides.stream()
                .map(
                        Slide::getThumbnailKey
                )
                .forEach(
                        keys::add
                );

        slideRepository.deleteAll(
                slides
        );

        documentRepository.delete(
                document
        );

        deleteAfterCommit(
                keys
        );
    }

    private Document getDocument(
            UUID documentId
    ) {
        return documentRepository
                .findById(
                        documentId
                )
                .orElseThrow(() ->
                        new BusinessException(
                                DocumentErrorCode
                                        .DOCUMENT_NOT_FOUND
                        )
                );
    }

    private SpaceMember requireApprovedMember(
            UUID spaceId,
            User currentUser
    ) {
        return spaceMemberRepository
                .findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                        spaceId,
                        currentUser.getId(),
                        SpaceMemberStatus.APPROVED
                )
                .orElseThrow(() ->
                        new BusinessException(
                                DocumentErrorCode
                                        .DOCUMENT_ACCESS_DENIED
                        )
                );
    }

    private SpaceMember requireDocumentManager(
            UUID spaceId,
            User currentUser
    ) {
        SpaceMember member =
                requireApprovedMember(
                        spaceId,
                        currentUser
                );

        if (
                member.getRole()
                        == SpaceMemberRole.PROFESSOR
        ) {
            return member;
        }

        if (
                member.getRole()
                        == SpaceMemberRole.ASSISTANT
                        && permissionRepository
                        .existsBySpaceMemberIdAndPermission(
                                member.getId(),
                                PermissionType
                                        .LECTURE_MATERIAL_MANAGE
                        )
        ) {
            return member;
        }

        throw new BusinessException(
                DocumentErrorCode
                        .DOCUMENT_ACCESS_DENIED
        );
    }

    private String normalizeTitle(
            String title
    ) {
        if (title == null) {
            throw new BusinessException(
                    DocumentErrorCode
                            .INVALID_DOCUMENT_TITLE
            );
        }

        String normalized =
                title.trim();

        if (
                normalized.isEmpty()
                        || normalized.length() > 255
        ) {
            throw new BusinessException(
                    DocumentErrorCode
                            .INVALID_DOCUMENT_TITLE
            );
        }

        return normalized;
    }

    /**
     * Document 저장 Transaction이 성공한 뒤에만
     * 강의자료 AI 분석을 시작한다.
     *
     * AI 처리 실패가 이미 등록된 강의자료를
     * Rollback시키지 않도록 분리한다.
     */
    private void registerDocumentAiProcessingAfterCommit(
            UUID documentId,
            byte[] pdfBytes
    ) {
        byte[] copiedPdfBytes =
                pdfBytes.clone();

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {

                            @Override
                            public void afterCommit() {

                                try {
                                    documentAiProcessingService
                                            .processAsync(
                                                    documentId,
                                                    copiedPdfBytes
                                            );

                                } catch (Exception exception) {

                                    log.warn(
                                            "Document AI processing failed. documentId={}",
                                            documentId,
                                            exception
                                    );
                                }
                            }
                        }
                );
    }

    private void deleteAfterRollback(
            List<String> keys
    ) {
        List<String> immutableKeys =
                List.copyOf(keys);

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {

                            @Override
                            public void afterCompletion(
                                    int status
                            ) {
                                if (
                                        status
                                                == TransactionSynchronization
                                                .STATUS_ROLLED_BACK
                                ) {
                                    cleanup(
                                            immutableKeys,
                                            null
                                    );
                                }
                            }
                        }
                );
    }

    private void deleteAfterCommit(
            List<String> keys
    ) {
        List<String> immutableKeys =
                List.copyOf(keys);

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {

                            @Override
                            public void afterCommit() {

                                cleanup(
                                        immutableKeys,
                                        null
                                );
                            }
                        }
                );
    }

    private void cleanup(
            List<String> keys,
            RuntimeException original
    ) {
        for (String key : keys) {

            try {
                storage.delete(
                        key
                );

            } catch (
                    RuntimeException cleanupFailure
            ) {

                if (original != null) {
                    original.addSuppressed(
                            cleanupFailure
                    );
                }
            }
        }
    }
}
