package com.tikitaka.document.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import com.tikitaka.document.ai.dto.DocumentAnalyzeRequest;

@Service
public class PdfTextExtractService {

    public List<DocumentAnalyzeRequest.PageContent> extractPages(
            byte[] pdfBytes
    ) {
        validatePdfBytes(pdfBytes);

        try (
                PDDocument document =
                        Loader.loadPDF(pdfBytes)
        ) {
            PDFTextStripper stripper =
                    new PDFTextStripper();

            List<DocumentAnalyzeRequest.PageContent> pages =
                    new ArrayList<>();

            int pageCount =
                    document.getNumberOfPages();

            for (
                    int pageIndex = 0;
                    pageIndex < pageCount;
                    pageIndex++
            ) {
                int pageNumber =
                        pageIndex + 1;

                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);

                String text =
                        normalize(
                                stripper.getText(document)
                        );

                pages.add(
                        new DocumentAnalyzeRequest.PageContent(
                                pageNumber,
                                text
                        )
                );
            }

            return pages;

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to extract text from PDF.",
                    exception
            );
        }
    }

    private void validatePdfBytes(
            byte[] pdfBytes
    ) {
        if (pdfBytes == null
                || pdfBytes.length == 0) {

            throw new IllegalArgumentException(
                    "PDF bytes must not be empty."
            );
        }
    }

    private String normalize(
            String text
    ) {
        if (text == null) {
            return "";
        }

        return text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}