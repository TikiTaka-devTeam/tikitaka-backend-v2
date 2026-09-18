package com.tikitaka.question.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.tikitaka.question.ai.dto.AnswerTranscribeResponse;

@Component
public class AnswerAiClient {

    private final RestClient restClient;

    public AnswerAiClient(
            RestClient.Builder builder,
            @Value(
                    "${ai.base-url:http://localhost:8000}"
            )
            String aiBaseUrl
    ) {
        this.restClient =
                builder
                        .baseUrl(
                                aiBaseUrl
                        )
                        .build();
    }

    public AnswerTranscribeResponse transcribe(
            byte[] audioBytes,
            String filename,
            String contentType
    ) {
        validateAudio(
                audioBytes,
                filename,
                contentType
        );

        ByteArrayResource audioResource =
                new ByteArrayResource(
                        audioBytes
                ) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                };

        HttpHeaders partHeaders =
                new HttpHeaders();

        partHeaders
                .setContentType(
                        MediaType
                                .parseMediaType(
                                        contentType
                                )
                );

        partHeaders
                .setContentDispositionFormData(
                        "file",
                        filename
                );

        HttpEntity<ByteArrayResource>
                filePart =
                new HttpEntity<>(
                        audioResource,
                        partHeaders
                );

        MultiValueMap<String, Object> body =
                new LinkedMultiValueMap<>();

        body.add(
                "file",
                filePart
        );

        try {
            AnswerTranscribeResponse response =
                    restClient
                            .post()
                            .uri(
                                    "/ai/answers/transcribe"
                            )
                            .contentType(
                                    MediaType
                                            .MULTIPART_FORM_DATA
                            )
                            .body(
                                    body
                            )
                            .retrieve()
                            .body(
                                    AnswerTranscribeResponse.class
                            );

            validateResponse(
                    response
            );

            return response;

        } catch (RestClientException exception) {

            throw new IllegalStateException(
                    "Failed to call AI answer transcription API.",
                    exception
            );
        }
    }

    private void validateAudio(
            byte[] audioBytes,
            String filename,
            String contentType
    ) {
        if (audioBytes == null
                || audioBytes.length == 0) {

            throw new IllegalArgumentException(
                    "Audio file must not be empty."
            );
        }

        if (filename == null
                || filename.isBlank()) {

            throw new IllegalArgumentException(
                    "Audio filename must not be empty."
            );
        }

        if (contentType == null
                || contentType.isBlank()) {

            throw new IllegalArgumentException(
                    "Audio content type must not be empty."
            );
        }
    }

    private void validateResponse(
            AnswerTranscribeResponse response
    ) {
        if (response == null) {

            throw new IllegalStateException(
                    "AI transcription response must not be null."
            );
        }

        if (response.transcript() == null
                || response
                .transcript()
                .isBlank()) {

            throw new IllegalStateException(
                    "AI transcript must not be empty."
            );
        }

        if (response.normalizedContent()
                == null
                || response
                .normalizedContent()
                .isBlank()) {

            throw new IllegalStateException(
                    "AI normalized answer content must not be empty."
            );
        }
    }
}