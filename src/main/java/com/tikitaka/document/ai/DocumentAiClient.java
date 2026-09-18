package com.tikitaka.document.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.tikitaka.document.ai.dto.DocumentAnalyzeRequest;
import com.tikitaka.document.ai.dto.DocumentAnalyzeResponse;

@Component
public class DocumentAiClient {

    private final RestClient restClient;

    public DocumentAiClient(
            RestClient.Builder builder,
            @Value("${ai.base-url:http://localhost:8000}") String aiBaseUrl
    ) {
        this.restClient = builder
                .baseUrl(aiBaseUrl)
                .build();
    }

    public DocumentAnalyzeResponse analyzeDocument(
            DocumentAnalyzeRequest request
    ) {
        try {
            DocumentAnalyzeResponse response =
                    restClient.post()
                            .uri("/ai/documents/analyze")
                            .body(request)
                            .retrieve()
                            .body(DocumentAnalyzeResponse.class);

            if (response == null) {
                throw new IllegalStateException(
                        "AI document analysis response is empty."
                );
            }

            if (response.categories() == null) {
                throw new IllegalStateException(
                        "AI document categories must not be null."
                );
            }

            return response;

        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Failed to call AI document analysis API.",
                    exception
            );
        }
    }
}