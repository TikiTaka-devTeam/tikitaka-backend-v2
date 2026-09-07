package com.tikitaka.document.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.springframework.stereotype.Component;

import com.tikitaka.document.entity.RevisionPage;
import com.tikitaka.document.entity.RevisionPageStatus;
import com.tikitaka.document.entity.RevisionSourceType;
import com.tikitaka.document.exception.DocumentErrorCode;
import com.tikitaka.global.exception.BusinessException;

@Component
public class RevisionPdfComposer {
    public byte[] compose(byte[] originalPdf, byte[] sourcePdf, Iterable<RevisionPage> pages,
            Map<UUID, Integer> originalPageNumbers, Map<UUID, Integer> sourcePageNumbers) {
        try (PDDocument original = Loader.loadPDF(originalPdf);
             PDDocument source = sourcePdf == null ? null : Loader.loadPDF(sourcePdf);
             PDDocument result = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (RevisionPage page : pages) {
                if (page.getSourceType() == RevisionSourceType.REVISION
                        && page.getStatus() == RevisionPageStatus.DELETE_PENDING) continue;
                PDPage sourcePage;
                if (page.getSourceType() == RevisionSourceType.ORIGINAL) {
                    sourcePage = original.getPage(originalPageNumbers.get(page.getOriginalSlide().getId()) - 1);
                    if (page.getStatus() == RevisionPageStatus.DELETE_PENDING) result.addPage(new PDPage(sourcePage.getMediaBox()));
                    else result.importPage(sourcePage);
                } else {
                    sourcePage = source.getPage(sourcePageNumbers.get(page.getRevisionSlide().getId()) - 1);
                    result.importPage(sourcePage);
                }
            }
            result.save(output);
            return output.toByteArray();
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(DocumentErrorCode.PDF_PROCESSING_FAILED, exception);
        }
    }
}
