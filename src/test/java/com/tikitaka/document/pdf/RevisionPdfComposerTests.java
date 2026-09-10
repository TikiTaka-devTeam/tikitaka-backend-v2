package com.tikitaka.document.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import com.tikitaka.document.entity.RevisionPage;
import com.tikitaka.document.entity.RevisionPageStatus;
import com.tikitaka.document.entity.RevisionSlide;
import com.tikitaka.document.entity.RevisionSourceType;
import com.tikitaka.document.entity.Slide;

class RevisionPdfComposerTests {

    private final RevisionPdfComposer composer = new RevisionPdfComposer();

    @Test
    void deletedOriginalBecomesBlankPageAndDeletedRevisionIsExcluded() throws Exception {
        UUID originalSlideId = UUID.randomUUID();
        UUID revisionSlideId = UUID.randomUUID();
        RevisionPage original = originalPage(originalSlideId, RevisionPageStatus.DELETE_PENDING);
        RevisionPage deletedRevision = revisionPage(revisionSlideId, RevisionPageStatus.DELETE_PENDING);

        byte[] result = composer.compose(
                pdf(PDRectangle.A4),
                pdf(PDRectangle.LETTER),
                List.of(original, deletedRevision),
                Map.of(originalSlideId, 1),
                Map.of(revisionSlideId, 1));

        try (PDDocument document = Loader.loadPDF(result)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(document.getPage(0).getMediaBox().getWidth()).isEqualTo(PDRectangle.A4.getWidth());
            assertThat(document.getPage(0).getMediaBox().getHeight()).isEqualTo(PDRectangle.A4.getHeight());
        }
    }

    @Test
    void activeRevisionPageIsIncludedInFinalPdf() throws Exception {
        UUID revisionSlideId = UUID.randomUUID();
        RevisionPage revision = revisionPage(revisionSlideId, RevisionPageStatus.ACTIVE);

        byte[] result = composer.compose(
                pdf(PDRectangle.A4),
                pdf(PDRectangle.LETTER),
                List.of(revision),
                Map.of(),
                Map.of(revisionSlideId, 1));

        try (PDDocument document = Loader.loadPDF(result)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(document.getPage(0).getMediaBox().getWidth()).isEqualTo(PDRectangle.LETTER.getWidth());
        }
    }

    private RevisionPage originalPage(UUID slideId, RevisionPageStatus status) {
        Slide slide = mock(Slide.class);
        when(slide.getId()).thenReturn(slideId);
        RevisionPage page = mock(RevisionPage.class);
        when(page.getSourceType()).thenReturn(RevisionSourceType.ORIGINAL);
        when(page.getStatus()).thenReturn(status);
        when(page.getOriginalSlide()).thenReturn(slide);
        return page;
    }

    private RevisionPage revisionPage(UUID slideId, RevisionPageStatus status) {
        RevisionSlide slide = mock(RevisionSlide.class);
        when(slide.getId()).thenReturn(slideId);
        RevisionPage page = mock(RevisionPage.class);
        when(page.getSourceType()).thenReturn(RevisionSourceType.REVISION);
        when(page.getStatus()).thenReturn(status);
        when(page.getRevisionSlide()).thenReturn(slide);
        return page;
    }

    private byte[] pdf(PDRectangle rectangle) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage(rectangle));
            document.save(output);
            return output.toByteArray();
        }
    }
}
