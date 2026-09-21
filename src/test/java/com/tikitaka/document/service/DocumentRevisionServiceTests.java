package com.tikitaka.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import com.tikitaka.document.dto.request.DocumentRevisionCompleteRequest;
import com.tikitaka.document.entity.Document;
import com.tikitaka.document.entity.DocumentRevision;
import com.tikitaka.document.entity.RevisionSlide;
import com.tikitaka.document.entity.RevisionStatus;
import com.tikitaka.document.exception.DocumentErrorCode;
import com.tikitaka.document.pdf.PdfProcessor;
import com.tikitaka.document.repository.DocumentRepository;
import com.tikitaka.document.repository.DocumentRevisionRepository;
import com.tikitaka.document.repository.RevisionOperationRepository;
import com.tikitaka.document.repository.RevisionPageRepository;
import com.tikitaka.document.repository.RevisionSlideRepository;
import com.tikitaka.document.repository.SlideRepository;
import com.tikitaka.document.storage.DocumentStorage;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.s3.S3FileValidator;
import com.tikitaka.space.entity.Space;
import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberRole;
import com.tikitaka.space.entity.SpaceMemberStatus;
import com.tikitaka.space.repository.SpaceMemberPermissionRepository;
import com.tikitaka.space.repository.SpaceMemberRepository;
import com.tikitaka.user.entity.User;

class DocumentRevisionServiceTests {

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentRevisionRepository revisionRepository = mock(DocumentRevisionRepository.class);
    private final RevisionPageRepository revisionPageRepository = mock(RevisionPageRepository.class);
    private final RevisionSlideRepository revisionSlideRepository = mock(RevisionSlideRepository.class);
    private final RevisionOperationRepository revisionOperationRepository = mock(RevisionOperationRepository.class);
    private final SlideRepository slideRepository = mock(SlideRepository.class);
    private final SpaceMemberRepository memberRepository = mock(SpaceMemberRepository.class);
    private final SpaceMemberPermissionRepository permissionRepository = mock(SpaceMemberPermissionRepository.class);
    private final S3FileValidator fileValidator = mock(S3FileValidator.class);
    private final PdfProcessor pdfProcessor = mock(PdfProcessor.class);
    private final DocumentStorage storage = mock(DocumentStorage.class);
    private final DocumentRevisionCompletionWorker completionWorker = mock(DocumentRevisionCompletionWorker.class);

    private final DocumentRevisionService service = new DocumentRevisionService(
            documentRepository, revisionRepository, revisionPageRepository, revisionSlideRepository,
            revisionOperationRepository, slideRepository, memberRepository, permissionRepository,
            fileValidator, pdfProcessor, storage, completionWorker);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createsNewRevisionWhenNoActiveRevisionExists() {
        UUID documentId = UUID.randomUUID();
        User user = manager();
        Document document = document();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(revisionRepository.findFirstByDocumentIdAndStatusIn(documentId, List.of(RevisionStatus.EDITING, RevisionStatus.PROCESSING)))
                .thenReturn(Optional.empty());
        when(revisionRepository.save(any(DocumentRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(slideRepository.findAllByDocumentIdOrderByPageNumberAsc(documentId)).thenReturn(List.of());
        when(revisionPageRepository.findAllByRevisionIdOrderByPositionAsc(null)).thenReturn(List.of());
        when(revisionOperationRepository.findAllByRevisionIdOrderBySequenceAsc(null)).thenReturn(List.of());

        var result = service.createRevision(documentId, user);

        assertThat(result.created()).isTrue();
        assertThat(result.detail().status()).isEqualTo(RevisionStatus.EDITING);
        assertThat(result.detail().previewVersion()).isZero();
    }

    @Test
    void resumesCurrentEditorsEditingRevision() {
        UUID documentId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        User user = manager();
        Document document = document();
        DocumentRevision revision = mock(DocumentRevision.class);
        RevisionSlide sourceSlide = mock(RevisionSlide.class);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(revisionRepository.findFirstByDocumentIdAndStatusIn(documentId, List.of(RevisionStatus.EDITING, RevisionStatus.PROCESSING)))
                .thenReturn(Optional.of(revision));
        when(revision.getStatus()).thenReturn(RevisionStatus.EDITING);
        when(revision.getEditor()).thenReturn(user);
        when(revision.getId()).thenReturn(revisionId);
        when(revision.getDocument()).thenReturn(document);
        when(revision.getBaseDocumentVersion()).thenReturn(3);
        when(revision.getPreviewVersion()).thenReturn(2);
        when(revision.getOperationCursorSequence()).thenReturn(null);
        when(revision.getSourceFileName()).thenReturn("additional.pdf");
        when(revision.getSourcePdfKey()).thenReturn("revisions/source.pdf");
        when(revision.getSourcePageCount()).thenReturn(1);
        when(sourceSlide.getSourcePageNumber()).thenReturn(1);
        when(sourceSlide.getThumbnailKey()).thenReturn("revisions/slide-1.png");
        when(revisionSlideRepository.findAllByRevisionIdOrderBySourcePageNumberAsc(revisionId)).thenReturn(List.of(sourceSlide));
        when(revisionPageRepository.findAllByRevisionIdOrderByPositionAsc(revisionId)).thenReturn(List.of());
        when(revisionOperationRepository.findAllByRevisionIdOrderBySequenceAsc(revisionId)).thenReturn(List.of());
        when(storage.presignedGetUrl("revisions/source.pdf")).thenReturn("https://signed.example/source.pdf");
        when(storage.presignedGetUrl("revisions/slide-1.png")).thenReturn("https://signed.example/slide-1.png");

        var result = service.createRevision(documentId, user);

        assertThat(result.created()).isFalse();
        assertThat(result.detail().revisionId()).isEqualTo(revisionId);
        assertThat(result.detail().sourceFileName()).isEqualTo("additional.pdf");
        assertThat(result.detail().sourcePdfUrl()).isEqualTo("https://signed.example/source.pdf");
        assertThat(result.detail().revisionSlides()).hasSize(1);
        verify(revision).resume();
    }

    @Test
    void rejectsProcessingRevision() {
        UUID documentId = UUID.randomUUID();
        User user = manager();
        DocumentRevision revision = mock(DocumentRevision.class);
        Document document = document();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(revisionRepository.findFirstByDocumentIdAndStatusIn(documentId, List.of(RevisionStatus.EDITING, RevisionStatus.PROCESSING)))
                .thenReturn(Optional.of(revision));
        when(revision.getStatus()).thenReturn(RevisionStatus.PROCESSING);

        assertThatThrownBy(() -> service.createRevision(documentId, user))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(DocumentErrorCode.REVISION_NOT_EDITABLE);
    }

    @Test
    void completesRevisionWithNormalizedTitle() {
        CompleteFixture fixture = completeFixture();
        TransactionSynchronizationManager.initSynchronization();

        service.complete(
                fixture.documentId(),
                fixture.revisionId(),
                new DocumentRevisionCompleteRequest(2, "  Updated lecture  "),
                fixture.user());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCommit());

        verify(fixture.revision()).startProcessing();
        verify(completionWorker).completeAsync(fixture.revisionId(), "Updated lecture");
    }

    @Test
    void keepsExistingTitleWhenCompleteTitleIsNull() {
        CompleteFixture fixture = completeFixture();
        TransactionSynchronizationManager.initSynchronization();

        service.complete(
                fixture.documentId(),
                fixture.revisionId(),
                new DocumentRevisionCompleteRequest(2, null),
                fixture.user());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCommit());

        verify(completionWorker).completeAsync(fixture.revisionId(), "Lecture");
    }

    @Test
    void rejectsBlankCompleteTitle() {
        CompleteFixture fixture = completeFixture();

        assertThatThrownBy(() -> service.complete(
                fixture.documentId(),
                fixture.revisionId(),
                new DocumentRevisionCompleteRequest(2, "   "),
                fixture.user()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(DocumentErrorCode.INVALID_DOCUMENT_TITLE);

        verify(fixture.revision(), never()).startProcessing();
        verify(completionWorker, never()).completeAsync(any(), any());
    }

    private CompleteFixture completeFixture() {
        UUID documentId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        User user = mock(User.class);
        Document document = mock(Document.class);
        DocumentRevision revision = mock(DocumentRevision.class);
        UUID userId = UUID.randomUUID();

        when(user.getId()).thenReturn(userId);
        when(document.getId()).thenReturn(documentId);
        when(document.getVersion()).thenReturn(3);
        when(document.getTitle()).thenReturn("Lecture");
        when(revision.getId()).thenReturn(revisionId);
        when(revision.getDocument()).thenReturn(document);
        when(revision.getEditor()).thenReturn(user);
        when(revision.getStatus()).thenReturn(RevisionStatus.EDITING);
        when(revision.getPreviewVersion()).thenReturn(2);
        when(revision.getBaseDocumentVersion()).thenReturn(3);
        when(revisionRepository.findByIdForUpdate(revisionId)).thenReturn(Optional.of(revision));

        return new CompleteFixture(documentId, revisionId, user, revision);
    }

    private record CompleteFixture(
            UUID documentId,
            UUID revisionId,
            User user,
            DocumentRevision revision
    ) {}

    private User manager() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        SpaceMember member = mock(SpaceMember.class);
        when(user.getId()).thenReturn(userId);
        when(member.getRole()).thenReturn(SpaceMemberRole.PROFESSOR);
        when(memberRepository.findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                any(UUID.class), any(UUID.class), any(SpaceMemberStatus.class))).thenReturn(Optional.of(member));
        return user;
    }

    private Document document() {
        Document document = mock(Document.class);
        Space space = mock(Space.class);
        when(document.getSpace()).thenReturn(space);
        when(space.getId()).thenReturn(UUID.randomUUID());
        when(document.getVersion()).thenReturn(3);
        when(document.getTitle()).thenReturn("Lecture");
        return document;
    }
}
