package com.tikitaka.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.tikitaka.document.dto.DocumentNoteType;
import com.tikitaka.document.entity.Document;
import com.tikitaka.document.entity.Slide;
import com.tikitaka.document.pdf.NotePdfComposer;
import com.tikitaka.document.pdf.NotePdfComposer.Stroke;
import com.tikitaka.document.storage.DocumentStorage;
import com.tikitaka.note.entity.*;
import com.tikitaka.note.repository.*;
import com.tikitaka.user.entity.User;

class DocumentNoteExportServiceTests {
    private final SharedStrokeRepository shared = mock(SharedStrokeRepository.class);
    private final PrivateStrokeRepository personal = mock(PrivateStrokeRepository.class);
    private final NotePdfComposer composer = mock(NotePdfComposer.class);
    private final DocumentStorage storage = mock(DocumentStorage.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-04T00:00:00Z"), ZoneOffset.UTC);
    private final DocumentNoteExportService service = new DocumentNoteExportService(shared, personal, composer, storage, clock);
    private final Document document = mock(Document.class);
    private final User user = mock(User.class);

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void allQueriesOnlyRequestingUserAndDrawsPersonalAfterShared() {
        UUID documentId = UUID.randomUUID(), userId = UUID.randomUUID();
        when(document.getId()).thenReturn(documentId);
        when(document.getPdfKey()).thenReturn("original.pdf");
        when(user.getId()).thenReturn(userId);
        Slide slide = mock(Slide.class);
        when(slide.getPageNumber()).thenReturn(2);
        var points = List.of(Map.of("x_ratio", 0.2, "y_ratio", 0.3));
        var sharedStroke = SharedStroke.create(SharedLayer.create(slide), StrokeTool.PEN, points, null, "#ff0000", 2.0, 1.0, 0);
        var privateStroke = PrivateStroke.create(PrivateLayer.create(slide, user), StrokeTool.PEN, points, null, "#0000ff", 3.0, 1.0, 0);
        when(shared.findForExport(documentId)).thenReturn(List.of(sharedStroke));
        when(personal.findForExport(documentId, userId)).thenReturn(List.of(privateStroke));
        byte[] source = {1}, output = {2};
        when(storage.get("original.pdf")).thenReturn(source);
        when(composer.compose(eq(source), anyList())).thenReturn(output);
        String key = service.export(document, user, DocumentNoteType.ALL);
        assertThat(key).startsWith("exports/documents/" + documentId + "/" + userId + "/");
        ArgumentCaptor<List<Stroke>> strokes = ArgumentCaptor.forClass((Class) List.class);
        verify(composer).compose(eq(source), strokes.capture());
        assertThat(strokes.getValue()).extracting(Stroke::color).containsExactly("#ff0000", "#0000ff");
        assertThat(strokes.getValue()).extracting(Stroke::pageNumber).containsOnly(2);
        verify(storage).put(key, output, "application/pdf");
        verify(personal).findForExport(documentId, userId);
        verifyNoMoreInteractions(personal);
    }

    @Test
    void sharedWithoutNotesReturnsOriginalAndNeverReadsPrivateNotesOrPdf() {
        when(document.getPdfKey()).thenReturn("original.pdf");
        when(shared.findForExport(any())).thenReturn(List.of());
        assertThat(service.export(document, user, DocumentNoteType.SHARED)).isEqualTo("original.pdf");
        verifyNoInteractions(personal, composer, storage);
    }

    @Test
    void privateDoesNotReadSharedNotes() {
        when(document.getPdfKey()).thenReturn("original.pdf");
        when(personal.findForExport(any(), any())).thenReturn(List.of());
        assertThat(service.export(document, user, DocumentNoteType.PRIVATE)).isEqualTo("original.pdf");
        verifyNoInteractions(shared, composer, storage);
    }

    @Test
    void cleanupLeavesOneHourForDownloadingAndScopesDocumentDeletion() {
        service.cleanupExpiredExports();
        verify(storage).deleteOlderThan("exports/documents/", clock.instant().minusSeconds(3600));
        UUID documentId = UUID.randomUUID();
        service.deleteExports(documentId);
        verify(storage).deleteOlderThan("exports/documents/" + documentId + "/", Instant.MAX);
    }
}
