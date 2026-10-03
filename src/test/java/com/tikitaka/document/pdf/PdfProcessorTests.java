package com.tikitaka.document.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.tikitaka.document.exception.DocumentErrorCode;
import com.tikitaka.global.exception.BusinessException;

class PdfProcessorTests {
    private final PdfProcessor processor = new PdfProcessor();

    @Test
    void selectedRenderingMatchesFullRenderingWithoutCreatingOtherImages() throws Exception {
        byte[] pdf = pdfFile(4, false).getBytes();
        ProcessedPdf full = processor.process(pdf);
        SelectedPdf selected = processor.processSelected(pdf, java.util.Set.of(2));
        assertThat(selected.pageCount()).isEqualTo(4);
        assertThat(selected.pageThumbnails()).containsOnlyKeys(2);
        assertThat(selected.pageThumbnails().get(2)).isEqualTo(full.pageThumbnails().get(2));
        SelectedPdf validationOnly = processor.processSelected(pdf, java.util.Set.of());
        assertThat(validationOnly.pageCount()).isEqualTo(4);
        assertThat(validationOnly.pageThumbnails()).isEmpty();
    }

    @Test
    void selectedRenderingKeepsPageLimitAndEncryptionChecks() throws Exception {
        byte[] oversized = pdfFile(301, false).getBytes();
        assertThatThrownBy(() -> processor.processSelected(oversized, java.util.Set.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(DocumentErrorCode.PDF_PAGE_LIMIT_EXCEEDED);
        byte[] encrypted = pdfFile(1, true).getBytes();
        assertThatThrownBy(() -> processor.processSelected(encrypted, java.util.Set.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(DocumentErrorCode.ENCRYPTED_PDF_NOT_SUPPORTED);
    }

    @Test
    void calculatesPageCountAndCreatesPngThumbnails() throws Exception {
        MockMultipartFile file = pdfFile(2, false);

        ProcessedPdf result = processor.process(file);

        assertThat(result.pageCount()).isEqualTo(2);
        assertThat(result.pageThumbnails()).hasSize(2);
        assertThat(result.pageThumbnails().get(0))
                .startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);
    }

    @Test
    void rejectsFileWithoutPdfSignature() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "not-pdf".getBytes());

        assertThatThrownBy(() -> processor.process(file))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(DocumentErrorCode.INVALID_PDF_FILE);
    }

    @Test
    void rejectsPasswordProtectedPdf() throws Exception {
        MockMultipartFile file = pdfFile(1, true);

        assertThatThrownBy(() -> processor.process(file))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(DocumentErrorCode.ENCRYPTED_PDF_NOT_SUPPORTED);
    }

    private MockMultipartFile pdfFile(int pageCount, boolean encrypted) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int index = 0; index < pageCount; index++) {
                document.addPage(new PDPage(new org.apache.pdfbox.pdmodel.common.PDRectangle(612 + index * 2, 792)));
            }
            if (encrypted) {
                StandardProtectionPolicy policy = new StandardProtectionPolicy(
                        "owner-password", "user-password", new AccessPermission());
                policy.setEncryptionKeyLength(128);
                document.protect(policy);
            }
            document.save(output);
            return new MockMultipartFile(
                    "file", "lecture.pdf", "application/pdf", output.toByteArray());
        }
    }
}
