package com.tikitaka.document.service;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tikitaka.document.ai.DocumentAiClient;
import com.tikitaka.document.ai.dto.DocumentAnalyzeRequest;
import com.tikitaka.document.ai.dto.DocumentAnalyzeResponse;
import com.tikitaka.document.ai.dto.DocumentCategoryResult;
import com.tikitaka.document.entity.Document;
import com.tikitaka.document.repository.DocumentRepository;
import com.tikitaka.question.entity.QuestionCategory;
import com.tikitaka.question.repository.QuestionCategoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentAiProcessingService {

    private final PdfTextExtractService pdfTextExtractService;
    private final DocumentAiClient documentAiClient;

    private final DocumentRepository documentRepository;
    private final QuestionCategoryRepository questionCategoryRepository;

    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public void process(
            UUID documentId,
            Path pdfPath
    ) {
        Document document =
                documentRepository.findById(documentId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Document not found: " + documentId
                                )
                        );

        List<DocumentAnalyzeRequest.PageContent> pages =
                pdfTextExtractService.extractPages(pdfPath);

        validatePages(pages);

        DocumentAnalyzeRequest request =
                new DocumentAnalyzeRequest(
                        document.getId(),
                        document.getTitle(),
                        pages
                );

        DocumentAnalyzeResponse response =
                documentAiClient.analyzeDocument(request);

        validateResponse(response);

        transactionTemplate.executeWithoutResult(status ->
                saveCategories(
                        documentId,
                        response.categories()
                )
        );
    }

    private void saveCategories(
            UUID documentId,
            List<DocumentCategoryResult> results
    ) {
        Document document =
                documentRepository.findById(documentId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Document not found: " + documentId
                                )
                        );

        for (DocumentCategoryResult result : results) {

            String name =
                    result.name().trim();

            if (questionCategoryRepository
                    .existsByDocumentIdAndNameAndDeletedFalse(
                            documentId,
                            name
                    )) {
                continue;
            }

            String sourcePages =
                    serializeSourcePages(
                            result.sourcePages()
                    );

            QuestionCategory category =
                    QuestionCategory.createByAi(
                            document,
                            name,
                            sourcePages
                    );

            questionCategoryRepository.save(category);
        }
    }

    private String serializeSourcePages(
            List<Integer> sourcePages
    ) {
        try {
            return objectMapper.writeValueAsString(
                    sourcePages
            );

        } catch (JsonProcessingException exception) {

            throw new IllegalStateException(
                    "Failed to serialize category source pages.",
                    exception
            );
        }
    }

    private void validatePages(
            List<DocumentAnalyzeRequest.PageContent> pages
    ) {
        if (pages == null || pages.isEmpty()) {
            throw new IllegalStateException(
                    "PDF pages must not be empty."
            );
        }

        boolean hasText =
                pages.stream()
                        .anyMatch(page ->
                                page.text() != null
                                        && !page.text().isBlank()
                        );

        if (!hasText) {
            throw new IllegalStateException(
                    "PDF does not contain extractable text."
            );
        }
    }

    private void validateResponse(
            DocumentAnalyzeResponse response
    ) {
        if (response == null) {
            throw new IllegalStateException(
                    "AI document analysis response must not be null."
            );
        }

        if (response.categories() == null) {
            throw new IllegalStateException(
                    "AI document categories must not be null."
            );
        }

        for (DocumentCategoryResult category :
                response.categories()) {

            if (category.name() == null
                    || category.name().isBlank()) {

                throw new IllegalStateException(
                        "AI category name must not be empty."
                );
            }

            if (category.sourcePages() == null
                    || category.sourcePages().isEmpty()) {

                throw new IllegalStateException(
                        "AI category source pages must not be empty."
                );
            }

            boolean invalidPage =
                    category.sourcePages()
                            .stream()
                            .anyMatch(page ->
                                    page == null
                                            || page <= 0
                            );

            if (invalidPage) {
                throw new IllegalStateException(
                        "AI category source page must be positive."
                );
            }
        }
    }
}