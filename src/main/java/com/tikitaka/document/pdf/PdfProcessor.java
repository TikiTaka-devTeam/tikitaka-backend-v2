package com.tikitaka.document.pdf;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.document.exception.DocumentErrorCode;
import com.tikitaka.global.exception.BusinessException;

@Component
public class PdfProcessor {
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};
    private static final int MAX_PAGE_COUNT = 300;
    private static final float THUMBNAIL_DPI = 96F;

    public ProcessedPdf process(MultipartFile file) {
        try {
            return process(file.getBytes());
        } catch (IOException exception) {
            throw new BusinessException(DocumentErrorCode.PDF_PROCESSING_FAILED, exception);
        }
    }

    public ProcessedPdf process(byte[] bytes) {
        try {
            validateSignature(bytes);
            try (PDDocument document = Loader.loadPDF(bytes)) {
                if (document.isEncrypted()) {
                    throw new BusinessException(DocumentErrorCode.ENCRYPTED_PDF_NOT_SUPPORTED);
                }

                int pageCount = document.getNumberOfPages();
                if (pageCount < 1) {
                    throw new BusinessException(DocumentErrorCode.INVALID_PDF_FILE);
                }
                if (pageCount > MAX_PAGE_COUNT) {
                    throw new BusinessException(DocumentErrorCode.PDF_PAGE_LIMIT_EXCEEDED);
                }

                PDFRenderer renderer = new PDFRenderer(document);
                List<byte[]> thumbnails = new ArrayList<>(pageCount);
                for (int index = 0; index < pageCount; index++) {
                    BufferedImage image = renderer.renderImageWithDPI(index, THUMBNAIL_DPI, ImageType.RGB);
                    thumbnails.add(toPng(image));
                }
                return new ProcessedPdf(bytes, thumbnails);
            }
        } catch (InvalidPasswordException exception) {
            throw new BusinessException(DocumentErrorCode.ENCRYPTED_PDF_NOT_SUPPORTED, exception);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(DocumentErrorCode.PDF_PROCESSING_FAILED, exception);
        }
    }

    private void validateSignature(byte[] bytes) {
        if (bytes.length < PDF_SIGNATURE.length) {
            throw new BusinessException(DocumentErrorCode.INVALID_PDF_FILE);
        }
        for (int index = 0; index < PDF_SIGNATURE.length; index++) {
            if (bytes[index] != PDF_SIGNATURE[index]) {
                throw new BusinessException(DocumentErrorCode.INVALID_PDF_FILE);
            }
        }
    }

    private byte[] toPng(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IOException("PNG writer is not available");
            }
            return output.toByteArray();
        }
    }
}
