package com.tikitaka.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.tikitaka.document.entity.*;
import com.tikitaka.document.pdf.*;
import com.tikitaka.document.repository.*;
import com.tikitaka.document.storage.DocumentStorage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class DocumentRevisionCompletionWorkerTests {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings("unchecked")
    void finalNumbersMatchPdfAfterDeletedOrUndoneInsertionAndRedo(boolean redo) {
        var revisions = mock(DocumentRevisionRepository.class);
        var pages = mock(RevisionPageRepository.class);
        var sources = mock(RevisionSlideRepository.class);
        var slides = mock(SlideRepository.class);
        var storage = mock(DocumentStorage.class);
        var composer = mock(RevisionPdfComposer.class);
        var processor = mock(PdfProcessor.class);
        var worker = new DocumentRevisionCompletionWorker(mock(TransactionTemplate.class), revisions,
                pages, sources, slides, storage, composer, processor, mock(DocumentAiProcessingService.class));
        var document = mock(Document.class);
        var revision = mock(DocumentRevision.class);
        UUID documentId = UUID.randomUUID(), revisionId = UUID.randomUUID();
        when(document.getId()).thenReturn(documentId);
        when(document.getPdfKey()).thenReturn("original.pdf");
        when(document.getThumbnailKey()).thenReturn("original.png");
        when(revision.getDocument()).thenReturn(document);
        when(revision.getStatus()).thenReturn(RevisionStatus.PROCESSING);
        when(revision.getId()).thenReturn(revisionId);
        when(revision.getSourcePdfKey()).thenReturn("source.pdf");
        when(revisions.findByIdForUpdate(revisionId)).thenReturn(Optional.of(revision));

        Slide first = Slide.create(document, 1, "first.png");
        Slide last = Slide.create(document, 2, "last.png");
        UUID lastId = UUID.randomUUID();
        ReflectionTestUtils.setField(first, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(last, "id", lastId);
        RevisionSlide source = mock(RevisionSlide.class);
        when(source.getId()).thenReturn(UUID.randomUUID());
        when(source.getSourcePageNumber()).thenReturn(1);
        when(source.getThumbnailKey()).thenReturn("source.png");
        RevisionPage originalFirst = RevisionPage.original(revision, 1, first);
        RevisionPage skippedFirst = RevisionPage.revision(revision, 2, source);
        RevisionPage originalLast = RevisionPage.original(revision, 3, last);
        RevisionPage skippedLast = RevisionPage.revision(revision, 4, source);
        RevisionPage activeInsertion = RevisionPage.revision(revision, 5, source);
        List<RevisionPage> ordered = List.of(originalFirst, skippedFirst, originalLast, skippedLast, activeInsertion);
        ordered.forEach(page -> ReflectionTestUtils.setField(page, "id", UUID.randomUUID()));
        skippedFirst.markDeletePending();
        skippedLast.markDeletePending();
        originalLast.markDeletePending(); // Original deletion must retain a blank page and its ID.
        if (redo) skippedFirst.reactivate();
        when(pages.findAllByRevisionIdOrderByPositionAsc(revisionId)).thenReturn(ordered);
        when(slides.findAllByDocumentIdOrderByPageNumberAsc(documentId)).thenReturn(List.of(first, last));
        when(sources.findAllByRevisionIdOrderBySourcePageNumberAsc(revisionId)).thenReturn(List.of(source));
        byte[] pdf = {1};
        when(composer.compose(any(), any(), any(), any(), any())).thenReturn(pdf);
        int finalCount = redo ? 4 : 3;
        when(processor.process(pdf)).thenReturn(new ProcessedPdf(pdf,
                java.util.stream.IntStream.range(0, finalCount).mapToObj(i -> new byte[]{(byte) i}).toList()));
        TransactionSynchronizationManager.initSynchronization();
        try {
            ReflectionTestUtils.invokeMethod(worker, "complete", revisionId, "Lecture", new ArrayList<String>());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        ArgumentCaptor<List<Slide>> inserted = ArgumentCaptor.forClass(List.class);
        verify(slides).saveAll(inserted.capture());
        List<Slide> finalSlides = new ArrayList<>(List.of(first, last));
        finalSlides.addAll(inserted.getValue());
        assertThat(finalSlides.stream().map(Slide::getPageNumber).sorted().toList())
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, finalCount).boxed().toList());
        assertThat(last.getId()).isEqualTo(lastId);
        assertThat(last.getStatus()).isEqualTo(SlideStatus.PLACEHOLDER);
        assertThat(last.getPageNumber()).isEqualTo(redo ? 3 : 2);
        assertThat(activeInsertion.getPosition()).isEqualTo(5);
        verify(document).replace(eq("Lecture"), anyString(), anyString(), eq(finalCount));
        verify(revision).complete();
    }
}
