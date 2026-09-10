package com.tikitaka.document.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tikitaka.user.entity.User;

class DocumentRevisionEntityTests {

    @Test
    void revisionStartsAtPreviewVersionZeroWithoutOperationCursor() {
        Document document = mock(Document.class);
        when(document.getVersion()).thenReturn(3);

        DocumentRevision revision = DocumentRevision.create(document, mock(User.class));

        assertThat(revision.getBaseDocumentVersion()).isEqualTo(3);
        assertThat(revision.getPreviewVersion()).isZero();
        assertThat(revision.getOperationCursorSequence()).isNull();
    }

    @Test
    void originalRevisionPageCanBeMarkedForDeletionAndReactivated() {
        DocumentRevision revision = revision();
        Slide slide = Slide.create(mock(Document.class), 1, "thumbnail.png");
        RevisionPage page = RevisionPage.original(revision, 1, slide);

        page.markDeletePending();

        assertThat(page.getStatus()).isEqualTo(RevisionPageStatus.DELETE_PENDING);

        page.reactivate();

        assertThat(page.getStatus()).isEqualTo(RevisionPageStatus.ACTIVE);
    }

    @Test
    void slideKeepsIdentityWhenItBecomesPlaceholder() {
        Slide slide = Slide.create(mock(Document.class), 1, "original.png");

        slide.markPlaceholder("placeholder.png");

        assertThat(slide.getStatus()).isEqualTo(SlideStatus.PLACEHOLDER);
        assertThat(slide.getThumbnailKey()).isEqualTo("placeholder.png");
    }

    @Test
    void operationCanBeUndoneRedoneAndDiscarded() {
        RevisionOperation operation = RevisionOperation.create(
                revision(),
                UUID.randomUUID(),
                1,
                RevisionOperationType.INSERT,
                Map.of("position", 1),
                Map.of("page_ids", java.util.List.of()),
                1);

        operation.undo();
        assertThat(operation.getState()).isEqualTo(RevisionOperationState.UNDONE);

        operation.redo();
        assertThat(operation.getState()).isEqualTo(RevisionOperationState.APPLIED);

        operation.discard();
        assertThat(operation.getState()).isEqualTo(RevisionOperationState.DISCARDED);
    }

    @Test
    void processingRevisionCanBeMarkedAsFailed() {
        DocumentRevision revision = revision();

        revision.startProcessing();
        revision.fail();

        assertThat(revision.getStatus()).isEqualTo(RevisionStatus.FAILED);
        assertThat(revision.getCompletedAt()).isNull();
    }

    private DocumentRevision revision() {
        Document document = mock(Document.class);
        when(document.getVersion()).thenReturn(1);
        return DocumentRevision.create(document, mock(User.class));
    }
}
