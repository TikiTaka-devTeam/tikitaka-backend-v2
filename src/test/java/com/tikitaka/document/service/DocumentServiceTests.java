package com.tikitaka.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.document.dto.response.DocumentCreateResponse;
import com.tikitaka.document.dto.response.DocumentDownloadResponse;
import com.tikitaka.document.dto.response.DocumentListItemResponse;
import com.tikitaka.document.entity.Document;
import com.tikitaka.document.entity.Slide;
import com.tikitaka.document.exception.DocumentErrorCode;
import com.tikitaka.document.pdf.PdfProcessor;
import com.tikitaka.document.pdf.ProcessedPdf;
import com.tikitaka.document.repository.DocumentRepository;
import com.tikitaka.document.repository.SlideRepository;
import com.tikitaka.document.storage.DocumentStorage;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.s3.S3FileValidator;
import com.tikitaka.notification.service.NotificationService;
import com.tikitaka.search.entity.RecentDocumentView;
import com.tikitaka.search.repository.RecentDocumentViewRepository;
import com.tikitaka.space.entity.PermissionType;
import com.tikitaka.space.entity.Space;
import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberRole;
import com.tikitaka.space.entity.SpaceMemberStatus;
import com.tikitaka.space.repository.SpaceMemberPermissionRepository;
import com.tikitaka.space.repository.SpaceMemberRepository;
import com.tikitaka.user.entity.User;

class DocumentServiceTests {

    private final DocumentRepository documentRepository =
            mock(DocumentRepository.class);

    private final SlideRepository slideRepository =
            mock(SlideRepository.class);

    private final RecentDocumentViewRepository recentViewRepository =
            mock(RecentDocumentViewRepository.class);

    private final SpaceMemberRepository memberRepository =
            mock(SpaceMemberRepository.class);

    private final SpaceMemberPermissionRepository permissionRepository =
            mock(SpaceMemberPermissionRepository.class);

    private final S3FileValidator fileValidator =
            mock(S3FileValidator.class);

    private final PdfProcessor pdfProcessor =
            mock(PdfProcessor.class);

    private final DocumentStorage storage =
            mock(DocumentStorage.class);

    private final NotificationService notificationService =
            mock(NotificationService.class);

    private final DocumentService service =
            new DocumentService(
                    documentRepository,
                    slideRepository,
                    recentViewRepository,
                    memberRepository,
                    permissionRepository,
                    fileValidator,
                    pdfProcessor,
                    storage,
                    notificationService
            );

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void returnsPresignedThumbnailUrlsToApprovedMember() {
        UUID spaceId = UUID.randomUUID();
        User user = user();

        approve(
                spaceId,
                user,
                SpaceMemberRole.STUDENT
        );

        Document document = mock(Document.class);

        when(document.getThumbnailKey())
                .thenReturn("documents/thumbnail.png");

        when(
                documentRepository
                        .findAllBySpaceIdOrderByCreatedAtDescIdDesc(spaceId)
        ).thenReturn(
                List.of(document)
        );

        when(
                storage.presignedGetUrl(
                        "documents/thumbnail.png"
                )
        ).thenReturn(
                "https://signed.example/thumbnail.png"
        );

        List<DocumentListItemResponse> result =
                service.getDocuments(
                        spaceId,
                        user
                );

        assertThat(result)
                .hasSize(1);

        assertThat(
                result.get(0).thumbnailUrl()
        ).isEqualTo(
                "https://signed.example/thumbnail.png"
        );
    }

    @Test
    void allowsAssistantWithLectureMaterialPermissionToCreateDocument() {
        UUID spaceId = UUID.randomUUID();
        User user = user();

        SpaceMember member =
                approve(
                        spaceId,
                        user,
                        SpaceMemberRole.ASSISTANT
                );

        when(
                permissionRepository
                        .existsBySpaceMemberIdAndPermission(
                                member.getId(),
                                PermissionType.LECTURE_MATERIAL_MANAGE
                        )
        ).thenReturn(true);

        MultipartFile file =
                mock(MultipartFile.class);

        ProcessedPdf processed =
                new ProcessedPdf(
                        new byte[] {1, 2},
                        List.of(
                                new byte[] {3, 4}
                        )
                );

        when(
                pdfProcessor.process(file)
        ).thenReturn(processed);

        when(
                documentRepository.save(
                        any(Document.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        when(
                storage.presignedGetUrl(any())
        ).thenReturn(
                "https://signed.example/thumbnail.png"
        );

        TransactionSynchronizationManager
                .initSynchronization();

        DocumentCreateResponse response =
                service.createDocument(
                        spaceId,
                        " 1주차 ",
                        file,
                        user
                );

        assertThat(
                response.title()
        ).isEqualTo(
                "1주차"
        );

        assertThat(
                response.pageCount()
        ).isEqualTo(1);

        verify(
                documentRepository
        ).save(
                any(Document.class)
        );

        verify(
                slideRepository
        ).saveAll(
                any()
        );

        verify(
                notificationService
        ).createDocumentUploadedNotification(
                member.getSpace(),
                response.documentId()
        );
    }

    @Test
    void rejectsAssistantWithoutLectureMaterialPermission() {
        UUID spaceId = UUID.randomUUID();
        User user = user();

        SpaceMember member =
                approve(
                        spaceId,
                        user,
                        SpaceMemberRole.ASSISTANT
                );

        when(
                permissionRepository
                        .existsBySpaceMemberIdAndPermission(
                                member.getId(),
                                PermissionType.LECTURE_MATERIAL_MANAGE
                        )
        ).thenReturn(false);

        assertThatThrownBy(
                () ->
                        service.createDocument(
                                spaceId,
                                "1주차",
                                mock(MultipartFile.class),
                                user
                        )
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        DocumentErrorCode.DOCUMENT_ACCESS_DENIED
                );

        verify(
                pdfProcessor,
                never()
        ).process(
                any()
        );

        verify(
                notificationService,
                never()
        ).createDocumentUploadedNotification(
                any(),
                any()
        );
    }

    @Test
    void returnsPresignedPdfAndCreatesRecentView() {
        UUID spaceId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        User user = user();

        approve(
                spaceId,
                user,
                SpaceMemberRole.STUDENT
        );

        Space space =
                mock(Space.class);

        Document document =
                mock(Document.class);

        when(
                space.getId()
        ).thenReturn(
                spaceId
        );

        when(
                document.getSpace()
        ).thenReturn(
                space
        );

        when(
                document.getPdfKey()
        ).thenReturn(
                "documents/original.pdf"
        );

        when(
                documentRepository.findById(
                        documentId
                )
        ).thenReturn(
                Optional.of(document)
        );

        when(
                recentViewRepository
                        .findByUserIdAndDocumentId(
                                user.getId(),
                                documentId
                        )
        ).thenReturn(
                Optional.empty()
        );

        when(
                storage.presignedGetUrl(
                        "documents/original.pdf"
                )
        ).thenReturn(
                "https://signed.example/original.pdf"
        );

        DocumentDownloadResponse response =
                service.downloadDocument(
                        documentId,
                        user
                );

        assertThat(
                response.downloadUrl()
        ).isEqualTo(
                "https://signed.example/original.pdf"
        );

        verify(
                recentViewRepository
        ).save(
                any(RecentDocumentView.class)
        );
    }

    @Test
    void deletesSlidesBeforeDocumentAndCleansUpFilesOnlyAfterCommit() {
        UUID spaceId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        User user = user();

        SpaceMember member =
                approve(
                        spaceId,
                        user,
                        SpaceMemberRole.PROFESSOR
                );

        Document document =
                Document.create(
                        member.getSpace(),
                        "Lecture",
                        "thumbnail.png",
                        "original.pdf",
                        2
                );

        List<Slide> slides =
                List.of(
                        Slide.create(
                                document,
                                1,
                                "slide-1.png"
                        ),
                        Slide.create(
                                document,
                                2,
                                "slide-2.png"
                        )
                );

        when(
                documentRepository.findById(
                        documentId
                )
        ).thenReturn(
                Optional.of(document)
        );

        when(
                slideRepository
                        .findAllByDocumentIdOrderByPageNumberAsc(
                                documentId
                        )
        ).thenReturn(
                slides
        );

        TransactionSynchronizationManager
                .initSynchronization();

        service.deleteDocument(
                documentId,
                user
        );

        var order =
                inOrder(
                        slideRepository,
                        documentRepository
                );

        order.verify(
                slideRepository
        ).deleteAll(
                slides
        );

        order.verify(
                documentRepository
        ).delete(
                document
        );

        verify(
                storage,
                never()
        ).delete(
                any()
        );

        var synchronizations =
                TransactionSynchronizationManager
                        .getSynchronizations();

        assertThat(
                synchronizations
        ).hasSize(1);

        synchronizations
                .get(0)
                .afterCommit();

        verify(storage)
                .delete("original.pdf");

        verify(storage)
                .delete("thumbnail.png");

        verify(storage)
                .delete("slide-1.png");

        verify(storage)
                .delete("slide-2.png");
    }

    private SpaceMember approve(
            UUID spaceId,
            User user,
            SpaceMemberRole role
    ) {
        SpaceMember member =
                mock(SpaceMember.class);

        Space space =
                mock(Space.class);

        when(
                space.getId()
        ).thenReturn(
                spaceId
        );

        when(
                member.getId()
        ).thenReturn(
                UUID.randomUUID()
        );

        when(
                member.getSpace()
        ).thenReturn(
                space
        );

        when(
                member.getRole()
        ).thenReturn(
                role
        );

        when(
                memberRepository
                        .findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                                spaceId,
                                user.getId(),
                                SpaceMemberStatus.APPROVED
                        )
        ).thenReturn(
                Optional.of(member)
        );

        return member;
    }

    private User user() {
        User user =
                mock(User.class);

        when(
                user.getId()
        ).thenReturn(
                UUID.randomUUID()
        );

        return user;
    }
}