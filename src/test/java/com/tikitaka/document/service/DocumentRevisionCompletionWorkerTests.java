package com.tikitaka.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;

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
        int blankIndex = redo ? 2 : 1;
        when(processor.processSelected(eq(pdf), eq(java.util.Set.of(blankIndex))))
                .thenReturn(new SelectedPdf(finalCount, java.util.Map.of(blankIndex, new byte[]{0})));
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
        verify(processor, never()).process(any(byte[].class));
        verify(storage).copy(eq("first.png"), anyString());
        verify(storage, times(redo ? 2 : 1)).copy(eq("source.png"), anyString());
        verify(storage, times(2)).put(anyString(), any(), anyString()); // PDF and blank page only.
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORIGINAL", "REVISION", "BLANK"})
    @SuppressWarnings("unchecked")
    void representativeThumbnailMatchesFirstOutputPageAndSurvivesTemporaryCleanup(String firstType) {
        var revisions = mock(DocumentRevisionRepository.class);
        var pages = mock(RevisionPageRepository.class);
        var sources = mock(RevisionSlideRepository.class);
        var slides = mock(SlideRepository.class);
        var storage = mock(DocumentStorage.class);
        var composer = mock(RevisionPdfComposer.class);
        var processor = mock(PdfProcessor.class);
        var document = mock(Document.class);
        var revision = mock(DocumentRevision.class);
        UUID documentId = UUID.randomUUID(), revisionId = UUID.randomUUID();
        when(document.getId()).thenReturn(documentId);
        when(document.getPdfKey()).thenReturn("old.pdf");
        when(document.getThumbnailKey()).thenReturn("old-cover.png");
        when(revision.getDocument()).thenReturn(document);
        when(revision.getStatus()).thenReturn(RevisionStatus.PROCESSING);
        when(revision.getSourcePdfKey()).thenReturn("source.pdf");
        when(revisions.findByIdForUpdate(revisionId)).thenReturn(Optional.of(revision));
        Slide original = Slide.create(document, 1, "original-page.png");
        ReflectionTestUtils.setField(original, "id", UUID.randomUUID());
        var source = mock(RevisionSlide.class);
        when(source.getId()).thenReturn(UUID.randomUUID());
        when(source.getSourcePageNumber()).thenReturn(1);
        when(source.getThumbnailKey()).thenReturn("temporary-source.png");
        RevisionPage first = firstType.equals("REVISION")
                ? RevisionPage.revision(revision, 1, source) : RevisionPage.original(revision, 1, original);
        ReflectionTestUtils.setField(first, "id", UUID.randomUUID());
        if (firstType.equals("BLANK")) first.markDeletePending();
        when(pages.findAllByRevisionIdOrderByPositionAsc(revisionId)).thenReturn(List.of(first));
        when(slides.findAllByDocumentIdOrderByPageNumberAsc(documentId)).thenReturn(List.of(original));
        when(sources.findAllByRevisionIdOrderBySourcePageNumberAsc(revisionId)).thenReturn(List.of(source));
        byte[] pdf = {1};
        when(composer.compose(any(), any(), any(), any(), any())).thenReturn(pdf);
        var indices = firstType.equals("BLANK") ? java.util.Set.of(0) : java.util.Set.<Integer>of();
        when(processor.processSelected(eq(pdf), eq(indices))).thenReturn(new SelectedPdf(1,
                firstType.equals("BLANK") ? java.util.Map.of(0, new byte[]{7}) : java.util.Map.of()));
        var worker = new DocumentRevisionCompletionWorker(mock(TransactionTemplate.class), revisions,
                pages, sources, slides, storage, composer, processor, mock(DocumentAiProcessingService.class));
        List<String> uploadedKeys = new ArrayList<>();
        TransactionSynchronizationManager.initSynchronization();
        try {
            ReflectionTestUtils.invokeMethod(worker, "complete", revisionId, "Lecture", uploadedKeys);
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        ArgumentCaptor<String> coverKey = ArgumentCaptor.forClass(String.class);
        verify(document).replace(eq("Lecture"), coverKey.capture(), anyString(), eq(1));
        if (firstType.equals("ORIGINAL")) {
            verify(storage).copy("original-page.png", coverKey.getValue());
            verify(storage, never()).delete("original-page.png");
        } else {
            String slideKey;
            if (firstType.equals("BLANK")) {
                slideKey = original.getThumbnailKey();
                verify(storage).put(eq(slideKey), aryEq(new byte[]{7}), eq("image/png"));
            } else {
                ArgumentCaptor<List<Slide>> created = ArgumentCaptor.forClass(List.class);
                verify(slides).saveAll(created.capture());
                slideKey = created.getValue().get(0).getThumbnailKey();
                verify(storage).copy("temporary-source.png", slideKey);
            }
            verify(storage).copy(slideKey, coverKey.getValue());
            verify(storage, never()).delete(slideKey);
        }
        verify(storage).delete("temporary-source.png");
        verify(storage).delete("old-cover.png");
        verify(storage, never()).delete(coverKey.getValue());
        assertThat(uploadedKeys).contains(coverKey.getValue());
        verify(processor).processSelected(pdf, indices);
    }
}
