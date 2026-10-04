package com.tikitaka.document.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import com.tikitaka.document.pdf.NotePdfComposer.Stroke;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.note.entity.StrokeTool;

class NotePdfComposerTests {
    private final NotePdfComposer composer = new NotePdfComposer();

    @Test
    void drawsAtDisplayCoordinatesForAllRotationsAndCropOffsets() throws Exception {
        for (int rotation : new int[]{0, 90, 180, 270}) {
            byte[] source = source(rotation);
            byte[] result = composer.compose(source, List.of(stroke("#ff0000", 1, 0.25)));
            try (PDDocument pdf = Loader.loadPDF(result)) {
                var image = new PDFRenderer(pdf).renderImage(0);
                var pixel = new Color(image.getRGB(image.getWidth() / 4, image.getHeight() / 4));
                assertThat(pixel.getRed()).as("rotation %s", rotation).isGreaterThan(230);
                assertThat(pixel.getGreen()).as("rotation %s", rotation).isLessThan(30);
                assertThat(pdf.getNumberOfPages()).isEqualTo(2);
                assertThat(pdf.getPage(0).getRotation()).isEqualTo(rotation);
                assertThat(pdf.getPage(0).getCropBox().getLowerLeftX()).isEqualTo(20);
                var untouched = new PDFRenderer(pdf).renderImage(1);
                assertThat(new Color(untouched.getRGB(10, 10))).isEqualTo(Color.BLUE);
            }
            try (PDDocument original = Loader.loadPDF(source)) {
                var image = new PDFRenderer(original).renderImage(0);
                assertThat(new Color(image.getRGB(image.getWidth() / 4, image.getHeight() / 4)))
                        .isEqualTo(Color.WHITE);
            }
        }
    }

    @Test
    void preservesOpacityAndDrawOrderIncludingSinglePoint() throws Exception {
        var dot = new Stroke(1, StrokeTool.PEN, List.of(point(0.25, 0.25)), "#ff0000", 0.1, 1);
        byte[] output = composer.compose(source(0), List.of(dot, stroke("#0000ff", 0.5, 0.25)));
        try (PDDocument pdf = Loader.loadPDF(output)) {
            var image = new PDFRenderer(pdf).renderImage(0);
            Color pixel = new Color(image.getRGB(image.getWidth() / 4, image.getHeight() / 4));
            assertThat(pixel.getRed()).isBetween(110, 145);
            assertThat(pixel.getBlue()).isBetween(110, 145);
            assertThat(pixel.getGreen()).isLessThan(20);
        }
    }

    @Test
    void rendersSinglePointAsDot() throws Exception {
        var dot = new Stroke(1, StrokeTool.PEN, List.of(point(0.25, 0.25)), "#ff0000", 0.1, 1);
        try (PDDocument pdf = Loader.loadPDF(composer.compose(source(0), List.of(dot)))) {
            var image = new PDFRenderer(pdf).renderImage(0);
            assertThat(new Color(image.getRGB(image.getWidth() / 4, image.getHeight() / 4)))
                    .isEqualTo(Color.RED);
        }
    }

    @Test
    void rejectsInvalidPdfOrStrokePage() throws Exception {
        assertThatThrownBy(() -> composer.compose(new byte[]{1, 2}, List.of()))
                .isInstanceOf(BusinessException.class);
        var invalid = new Stroke(3, StrokeTool.PEN, List.of(point(0.5, 0.5)), "#000000", 0.01, 1);
        assertThatThrownBy(() -> composer.compose(source(0), List.of(invalid)))
                .isInstanceOf(BusinessException.class);
    }

    private Stroke stroke(String color, double opacity, double y) {
        return new Stroke(1, StrokeTool.HIGHLIGHTER,
                List.of(point(0.1, y), point(0.4, y)), color, 0.1, opacity);
    }

    @Test
    void thicknessMatchesShortSideRatioAcrossToolsPageSizesRotationsAndZoom() throws Exception {
        for (StrokeTool tool : new StrokeTool[]{StrokeTool.PEN, StrokeTool.HIGHLIGHTER}) {
            for (int shortSide : new int[]{120, 400, 595}) {
                for (int rotation : new int[]{0, 90, 180, 270}) {
                    byte[] source;
                    try (PDDocument pdf = new PDDocument(); var output = new ByteArrayOutputStream()) {
                        PDPage page = new PDPage(new PDRectangle(shortSide, shortSide * 1.4f));
                        page.setRotation(rotation);
                        pdf.addPage(page);
                        pdf.save(output);
                        source = output.toByteArray();
                    }
                    double ratio = 0.02;
                    var line = new Stroke(1, tool, List.of(point(0.2, 0.5), point(0.8, 0.5)),
                            "#ff0000", ratio, 1);
                    try (PDDocument pdf = Loader.loadPDF(composer.compose(source, List.of(line)))) {
                        for (float zoom : new float[]{1, 2}) {
                            var image = new PDFRenderer(pdf).renderImage(0, zoom);
                            int coloredPixels = 0;
                            int x = image.getWidth() / 2;
                            for (int y = 0; y < image.getHeight(); y++) {
                                Color pixel = new Color(image.getRGB(x, y));
                                if (pixel.getRed() > 200 && pixel.getGreen() < 128) coloredPixels++;
                            }
                            double expectedPx = ratio * Math.min(image.getWidth(), image.getHeight());
                            assertThat((double) coloredPixels)
                                    .as("tool=%s shortSide=%s rotation=%s zoom=%s", tool, shortSide, rotation, zoom)
                                    .isCloseTo(expectedPx, org.assertj.core.data.Offset.offset(1.1));
                        }
                    }
                }
            }
        }
    }

    @Test
    void a4HalfMillimeterRatioProducesAboutOnePointFourTwoPoints() throws Exception {
        byte[] source;
        try (PDDocument pdf = new PDDocument(); var output = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage(new PDRectangle(595, 842)));
            pdf.save(output);
            source = output.toByteArray();
        }
        var line = new Stroke(1, StrokeTool.PEN, List.of(point(0.2, 0.5), point(0.8, 0.5)),
                "#000000", 0.00238, 1);
        try (PDDocument pdf = Loader.loadPDF(composer.compose(source, List.of(line)))) {
            var parser = new org.apache.pdfbox.pdfparser.PDFStreamParser(pdf.getPage(0));
            var tokens = parser.parse();
            java.util.ArrayList<Float> widths = new java.util.ArrayList<>();
            for (int i = 1; i < tokens.size(); i++) {
                if (tokens.get(i) instanceof org.apache.pdfbox.contentstream.operator.Operator operator
                        && operator.getName().equals("w")) {
                    widths.add(((org.apache.pdfbox.cos.COSNumber) tokens.get(i - 1)).floatValue());
                }
            }
            assertThat(widths).hasSize(1);
            assertThat(widths.get(0)).isCloseTo(1.4161f, org.assertj.core.data.Offset.offset(0.0001f));
        }
    }

    private Map<String, Double> point(double x, double y) {
        return Map.of("x_ratio", x, "y_ratio", y);
    }

    private byte[] source(int rotation) throws Exception {
        try (PDDocument pdf = new PDDocument(); var output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(new PDRectangle(300, 200));
            page.setCropBox(new PDRectangle(20, 30, 240, 120));
            page.setRotation(rotation);
            pdf.addPage(page);
            PDPage second = new PDPage(new PDRectangle(300, 200));
            pdf.addPage(second);
            try (var stream = new PDPageContentStream(pdf, second)) {
                stream.setNonStrokingColor(Color.BLUE);
                stream.addRect(0, 0, 300, 200);
                stream.fill();
            }
            pdf.save(output);
            return output.toByteArray();
        }
    }
}
