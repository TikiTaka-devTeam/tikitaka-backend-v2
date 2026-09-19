package com.tikitaka.question.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.tikitaka.document.ai.dto.DocumentAnalyzeRequest;
import com.tikitaka.document.entity.Document;
import com.tikitaka.document.entity.Slide;
import com.tikitaka.document.repository.DocumentRepository;
import com.tikitaka.document.repository.SlideRepository;
import com.tikitaka.document.service.PdfTextExtractService;
import com.tikitaka.document.storage.DocumentStorage;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuestionAiContextService {

    private static final int MAX_DOCUMENT_CONTEXT_CHARS = 60_000;

    private final DocumentRepository documentRepository;
    private final SlideRepository slideRepository;
    private final DocumentStorage documentStorage;
    private final PdfTextExtractService pdfTextExtractService;
    private final TransactionTemplate transactionTemplate;

    public Context load(
            UUID documentId,
            UUID slideId
    ) {
        ContextSource source =
                transactionTemplate.execute(status ->
                        loadSource(documentId, slideId)
                );

        if (source == null) {
            throw new IllegalStateException(
                    "Question AI context source transaction returned null."
            );
        }

        byte[] pdfBytes =
                documentStorage.get(source.pdfKey());

        List<DocumentAnalyzeRequest.PageContent> pages =
                pdfTextExtractService.extractPages(pdfBytes);

        String slideContext =
                source.slidePageNumber() == null
                        ? null
                        : findSlideContext(
                                pages,
                                source.slidePageNumber()
                        );

        String documentContext =
                buildDocumentContext(pages);

        return new Context(
                slideContext,
                documentContext
        );
    }

    private ContextSource loadSource(
            UUID documentId,
            UUID slideId
    ) {
        Document document =
                documentRepository.findById(documentId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Document not found: " + documentId
                                )
                        );

        Integer slidePageNumber = null;

        if (slideId != null) {
            Slide slide =
                    slideRepository.findById(slideId)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Slide not found: " + slideId
                                    )
                            );

            if (!slide.getDocument()
                    .getId()
                    .equals(documentId)) {
                throw new IllegalArgumentException(
                        "Slide does not belong to document. slideId="
                                + slideId
                );
            }

            slidePageNumber = slide.getPageNumber();
        }

        return new ContextSource(
                document.getPdfKey(),
                slidePageNumber
        );
    }

    private String findSlideContext(
            List<DocumentAnalyzeRequest.PageContent> pages,
            int pageNumber
    ) {
        return pages.stream()
                .filter(page -> page.page() == pageNumber)
                .map(DocumentAnalyzeRequest.PageContent::text)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String buildDocumentContext(
            List<DocumentAnalyzeRequest.PageContent> pages
    ) {
        StringBuilder builder = new StringBuilder();

        for (DocumentAnalyzeRequest.PageContent page : pages) {
            if (page.text() == null || page.text().isBlank()) {
                continue;
            }

            String block =
                    "[page "
                            + page.page()
                            + "]\n"
                            + page.text()
                            + "\n\n";

            int remaining =
                    MAX_DOCUMENT_CONTEXT_CHARS
                            - builder.length();

            if (remaining <= 0) {
                break;
            }

            if (block.length() <= remaining) {
                builder.append(block);
                continue;
            }

            builder.append(
                    block,
                    0,
                    remaining
            );
            break;
        }

        String context = builder.toString().trim();

        return context.isBlank()
                ? null
                : context;
    }

    public record Context(
            String slideContext,
            String documentContext
    ) {
    }

    private record ContextSource(
            String pdfKey,
            Integer slidePageNumber
    ) {
    }
}
