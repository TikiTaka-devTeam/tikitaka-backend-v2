package com.tikitaka.document.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.tikitaka.document.dto.DocumentNoteType;
import com.tikitaka.document.entity.Document;
import com.tikitaka.document.pdf.NotePdfComposer;
import com.tikitaka.document.pdf.NotePdfComposer.Stroke;
import com.tikitaka.document.storage.DocumentStorage;
import com.tikitaka.note.repository.PrivateStrokeRepository;
import com.tikitaka.note.repository.SharedStrokeRepository;
import com.tikitaka.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentNoteExportService {
    private final SharedStrokeRepository sharedStrokes;
    private final PrivateStrokeRepository privateStrokes;
    private final NotePdfComposer composer;
    private final DocumentStorage storage;
    private final Clock clock;

    public String export(Document document, User user, DocumentNoteType type) {
        List<Stroke> strokes = new ArrayList<>();
        // Shared notes are drawn first; personal notes remain on top.
        if (type.includesShared()) {
            sharedStrokes.findForExport(document.getId()).forEach(s -> strokes.add(new Stroke(
                    s.getLayer().getSlide().getPageNumber(), s.getTool(), s.getPoints(),
                    s.getColor(), s.getThickness(), s.getOpacity())));
        }
        if (type.includesPrivate()) {
            privateStrokes.findForExport(document.getId(), user.getId()).forEach(s -> strokes.add(new Stroke(
                    s.getLayer().getSlide().getPageNumber(), s.getTool(), s.getPoints(),
                    s.getColor(), s.getThickness(), s.getOpacity())));
        }
        if (strokes.isEmpty()) return document.getPdfKey();
        byte[] pdf = composer.compose(storage.get(document.getPdfKey()), strokes);
        String key = "exports/documents/" + document.getId() + "/" + user.getId()
                + "/" + UUID.randomUUID() + ".pdf";
        storage.put(key, pdf, "application/pdf");
        return key;
    }

    @Scheduled(fixedDelayString = "${document.download.cleanup-fixed-delay:PT15M}")
    public void cleanupExpiredExports() {
        // Download URLs expire in ten minutes; leave ample time for in-progress downloads.
        try {
            storage.deleteOlderThan("exports/documents/", clock.instant().minus(Duration.ofHours(1)));
        } catch (RuntimeException exception) {
            log.warn("Could not clean expired document exports", exception);
        }
    }

    public void deleteExports(UUID documentId) {
        try {
            storage.deleteOlderThan("exports/documents/" + documentId + "/", Instant.MAX);
        } catch (RuntimeException exception) {
            log.warn("Could not clean document exports. documentId={}", documentId, exception);
        }
    }
}
