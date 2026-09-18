package com.tikitaka.question.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.tikitaka.question.ai.dto.QuestionAnalyzeRequest;
import com.tikitaka.question.ai.dto.QuestionAnalyzeResponse;

@Component
public class QuestionAiClient {

    private final RestClient restClient;

    public QuestionAiClient(
            RestClient.Builder builder,
            @Value("${ai.base-url:http://localhost:8000}") String aiBaseUrl
    ) {
        this.restClient = builder
                .baseUrl(aiBaseUrl)
                .build();
    }

    public QuestionAnalyzeResponse analyzeQuestion(
            QuestionAnalyzeRequest request
    ) {
        try {
            QuestionAnalyzeResponse response = restClient.post()
                    .uri("/ai/questions/analyze")
                    .body(request)
                    .retrieve()
                    .body(QuestionAnalyzeResponse.class);

            if (response == null) {
                throw new IllegalStateException(
                        "AI question analysis response is empty."
                );
            }

            return response;

        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Failed to call AI question analysis API.",
                    exception
            );
        }
    }
}