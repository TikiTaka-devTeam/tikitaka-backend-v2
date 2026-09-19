package com.tikitaka.question.ai;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.tikitaka.question.ai.dto.ClusterTitleRequest;
import com.tikitaka.question.ai.dto.ClusterTitleResponse;

@Component
public class ClusterAiClient {

    private final RestClient restClient;

    public ClusterAiClient(
            @Qualifier("aiRestClient")
            RestClient restClient
    ) {
        this.restClient = restClient;
    }

    public ClusterTitleResponse generateTitle(
            ClusterTitleRequest request
    ) {
        try {
            ClusterTitleResponse response =
                    restClient.post()
                            .uri("/ai/clusters/title")
                            .body(request)
                            .retrieve()
                            .body(ClusterTitleResponse.class);

            if (response == null) {
                throw new IllegalStateException(
                        "AI cluster title response is empty."
                );
            }

            if (response.summaryTitle() == null
                    || response.summaryTitle().isBlank()) {
                throw new IllegalStateException(
                        "AI cluster title must not be empty."
                );
            }

            return response;

        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Failed to call AI cluster title API.",
                    exception
            );
        }
    }
}
