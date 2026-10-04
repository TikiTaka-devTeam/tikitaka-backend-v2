package com.tikitaka.document.pdf;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;
import com.tikitaka.document.exception.DocumentErrorCode;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.note.entity.StrokeTool;

@Component
public class NotePdfComposer {
    public record Stroke(int pageNumber, StrokeTool tool, List<Map<String, Double>> points,
                         String color, double thickness, double opacity) {}

    public byte[] compose(byte[] source, List<Stroke> strokes) {
        try (PDDocument pdf = Loader.loadPDF(source); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Map<Integer, List<Stroke>> pages = new LinkedHashMap<>();
            for (Stroke stroke : strokes) {
                if (stroke.pageNumber() < 1 || stroke.pageNumber() > pdf.getNumberOfPages()) {
                    throw new IOException("Stroke page is outside document");
                }
                pages.computeIfAbsent(stroke.pageNumber(), ignored -> new ArrayList<>()).add(stroke);
            }
            for (var entry : pages.entrySet()) {
                PDPage page = pdf.getPage(entry.getKey() - 1);
                try (var stream = new PDPageContentStream(pdf, page, AppendMode.APPEND, true, true)) {
                    for (Stroke stroke : entry.getValue()) draw(stream, page, stroke);
                }
            }
            pdf.save(output);
            return output.toByteArray();
        } catch (IOException | IllegalArgumentException exception) {
            throw new BusinessException(DocumentErrorCode.PDF_PROCESSING_FAILED, exception);
        }
    }

    private void draw(PDPageContentStream stream, PDPage page, Stroke stroke) throws IOException {
        if (stroke.tool() != StrokeTool.PEN && stroke.tool() != StrokeTool.HIGHLIGHTER) {
            throw new IOException("Unsupported saved stroke tool");
        }
        if (stroke.points() == null || stroke.points().isEmpty()) return;
        if (!Double.isFinite(stroke.thickness()) || stroke.thickness() <= 0
                || !Double.isFinite(stroke.opacity()) || stroke.opacity() < 0 || stroke.opacity() > 1) {
            throw new IOException("Invalid stroke appearance");
        }
        var box = page.getCropBox();
        float w = box.getWidth(), h = box.getHeight(), x = box.getLowerLeftX(), y = box.getLowerLeftY();
        int rotation = Math.floorMod(page.getRotation(), 360);
        float displayWidth = rotation == 90 || rotation == 270 ? h : w;
        float displayHeight = rotation == 90 || rotation == 270 ? w : h;
        Matrix transform = switch (rotation) {
            case 90 -> new Matrix(0, 1, 1, 0, x, y);
            case 180 -> new Matrix(-1, 0, 0, 1, x + w, y);
            case 270 -> new Matrix(0, -1, -1, 0, x + w, y + h);
            default -> new Matrix(1, 0, 0, -1, x, y + h);
        };
        stream.saveGraphicsState();
        stream.transform(transform);
        stream.addRect(0, 0, displayWidth, displayHeight);
        stream.clip();
        var state = new PDExtendedGraphicsState();
        state.setStrokingAlphaConstant((float) stroke.opacity());
        state.setNonStrokingAlphaConstant((float) stroke.opacity());
        stream.setGraphicsStateParameters(state);
        Color color = Color.decode(stroke.color());
        stream.setStrokingColor(color);
        stream.setNonStrokingColor(color);
        // Thickness uses PDF points, independent of the viewer's zoom level.
        stream.setLineWidth((float) stroke.thickness());
        stream.setLineCapStyle(1);
        stream.setLineJoinStyle(1);
        Map<String, Double> first = stroke.points().get(0);
        float px = coordinate(first, "x_ratio", displayWidth);
        float py = coordinate(first, "y_ratio", displayHeight);
        stream.moveTo(px, py);
        if (stroke.points().size() == 1) stream.lineTo(px, py);
        for (int i = 1; i < stroke.points().size(); i++) {
            var point = stroke.points().get(i);
            stream.lineTo(coordinate(point, "x_ratio", displayWidth), coordinate(point, "y_ratio", displayHeight));
        }
        stream.stroke();
        stream.restoreGraphicsState();
    }

    private float coordinate(Map<String, Double> point, String key, float size) throws IOException {
        Double value = point.get(key);
        if (value == null || !Double.isFinite(value) || value < 0 || value > 1) {
            throw new IOException("Invalid stroke coordinate");
        }
        return (float) (value * size);
    }
}
