package com.tikitaka.document.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.tikitaka.document.ai.DocumentAiClient;
import com.tikitaka.document.ai.dto.DocumentAnalyzeRequest;
import com.tikitaka.document.ai.dto.DocumentAnalyzeResponse;
import com.tikitaka.document.ai.dto.DocumentCategoryResult;
import com.tikitaka.document.entity.Document;
import com.tikitaka.document.repository.DocumentRepository;
import com.tikitaka.question.entity.CategorySourceType;
import com.tikitaka.question.entity.QuestionCategory;
import com.tikitaka.question.repository.QuestionCategoryMappingRepository;
import com.tikitaka.question.repository.QuestionCategoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentAiProcessingService {

    private final PdfTextExtractService pdfTextExtractService;
    private final DocumentAiClient documentAiClient;

    private final DocumentRepository documentRepository;
    private final QuestionCategoryRepository
            questionCategoryRepository;
    private final QuestionCategoryMappingRepository
            questionCategoryMappingRepository;
    private final TransactionTemplate transactionTemplate;

    public void process(
            UUID documentId,
            byte[] pdfBytes
    ) {
        DocumentSnapshot document =
                markProcessingAndLoadDocument(
                        documentId
                );

        try {
            List<DocumentAnalyzeRequest.PageContent> pages =
                    pdfTextExtractService.extractPages(
                            pdfBytes
                    );

            validatePages(
                    pages
            );

            DocumentAnalyzeRequest request =
                    new DocumentAnalyzeRequest(
                            document.id(),
                            document.title(),
                            pages
                    );

            DocumentAnalyzeResponse response =
                    documentAiClient.analyzeDocument(
                            request
                    );

            validateResponse(
                    response
            );

            transactionTemplate.executeWithoutResult(
                    status ->
                            reconcileCategoriesAndComplete(
                                    documentId,
                                    response.categories()
                            )
            );

        } catch (RuntimeException exception) {
            markFailed(
                    documentId
            );

            throw exception;
        }
    }

    private DocumentSnapshot
    markProcessingAndLoadDocument(
            UUID documentId
    ) {
        DocumentSnapshot snapshot =
                transactionTemplate.execute(
                        status -> {

                            Document document =
                                    getDocument(
                                            documentId
                                    );

                            document.startCategoryProcessing();

                            return new DocumentSnapshot(
                                    document.getId(),
                                    document.getTitle()
                            );
                        }
                );

        if (snapshot == null) {
            throw new IllegalStateException(
                    "Document AI processing transaction returned null."
            );
        }

        return snapshot;
    }

    private void reconcileCategoriesAndComplete(
            UUID documentId,
            List<DocumentCategoryResult> results
    ) {
        Document document =
                getDocument(
                        documentId
                );

        Map<String, DocumentCategoryResult>
                incomingByName =
                new LinkedHashMap<>();

        for (DocumentCategoryResult result : results) {

            String normalizedName =
                    normalizeName(
                            result.name()
                    );

            incomingByName.putIfAbsent(
                    normalizedKey(
                            normalizedName
                    ),
                    new DocumentCategoryResult(
                            normalizedName,
                            result.sourcePages()
                    )
            );
        }

        List<QuestionCategory>
                existingCategories =
                questionCategoryRepository
                        .findAllByDocumentIdAndDeletedFalse(
                                documentId
                        );

        Map<String, QuestionCategory>
                existingByName =
                new LinkedHashMap<>();

        for (QuestionCategory category
                : existingCategories) {

            existingByName.put(
                    normalizedKey(
                            category.getName()
                    ),
                    category
            );
        }

        for (
                Map.Entry<
                        String,
                        DocumentCategoryResult
                        > entry
                : incomingByName.entrySet()
        ) {

            QuestionCategory existing =
                    existingByName.get(
                            entry.getKey()
                    );

            if (existing != null) {

                if (
                        existing.getSourceType()
                                == CategorySourceType.AI
                ) {
                    existing.updateAiSourcePages(
                            entry
                                    .getValue()
                                    .sourcePages()
                    );
                }

                continue;
            }

            QuestionCategory category =
                    QuestionCategory.createByAi(
                            document,
                            entry.getValue()
                                    .name(),
                            entry.getValue()
                                    .sourcePages()
                    );

            questionCategoryRepository.save(
                    category
            );
        }

        for (
                QuestionCategory existing
                : existingCategories
        ) {

            if (
                    existing.getSourceType()
                            != CategorySourceType.AI
            ) {
                continue;
            }

            if (
                    incomingByName.containsKey(
                            normalizedKey(
                                    existing.getName()
                            )
                    )
            ) {
                continue;
            }

            if (
                    isCategoryInUse(
                            existing.getId()
                    )
            ) {
                continue;
            }

            existing.delete();
        }

        document.completeCategoryProcessing();
    }

    private boolean isCategoryInUse(
            UUID categoryId
    ) {
        return questionCategoryMappingRepository
                .existsByCategoryId(
                        categoryId
                );
    }

    private void markFailed(
            UUID documentId
    ) {
        transactionTemplate.executeWithoutResult(
                status ->
                        documentRepository
                                .findById(
                                        documentId
                                )
                                .ifPresent(
                                        Document::
                                                failCategoryProcessing
                                )
        );
    }

    private Document getDocument(
            UUID documentId
    ) {
        return documentRepository
                .findById(
                        documentId
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Document not found: "
                                                + documentId
                                )
                );
    }

    private String normalizeName(
            String name
    ) {
        return name == null
                ? ""
                : name.trim();
    }

    private String normalizedKey(
            String name
    ) {
        return normalizeName(
                name
        )
                .toLowerCase(
                        Locale.ROOT
                );
    }

    private void validatePages(
            List<DocumentAnalyzeRequest.PageContent>
                    pages
    ) {
        if (
                pages == null
                        || pages.isEmpty()
        ) {
            throw new IllegalStateException(
                    "PDF pages must not be empty."
            );
        }

        boolean hasText =
                pages.stream()
                        .anyMatch(
                                page ->
                                        page.text()
                                                != null
                                                && !page
                                                .text()
                                                .isBlank()
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

        for (
                DocumentCategoryResult category
                : response.categories()
        ) {
            validateCategory(
                    category
            );
        }
    }

    private void validateCategory(
            DocumentCategoryResult category
    ) {
        if (category == null) {
            throw new IllegalStateException(
                    "AI category must not be null."
            );
        }

        if (
                category.name() == null
                        || category.name()
                        .isBlank()
        ) {
            throw new IllegalStateException(
                    "AI category name must not be empty."
            );
        }

        if (
                category.sourcePages()
                        == null
                        || category
                        .sourcePages()
                        .isEmpty()
        ) {
            throw new IllegalStateException(
                    "AI category source pages must not be empty."
            );
        }

        boolean invalidPage =
                category
                        .sourcePages()
                        .stream()
                        .anyMatch(
                                page ->
                                        page == null
                                                || page <= 0
                        );

        if (invalidPage) {
            throw new IllegalStateException(
                    "AI category source page must be positive."
            );
        }
    }

    private record DocumentSnapshot(
            UUID id,
            String title
    ) {
    }
}