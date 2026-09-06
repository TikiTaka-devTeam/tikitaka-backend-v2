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
                document.addPage(new PDPage());
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
