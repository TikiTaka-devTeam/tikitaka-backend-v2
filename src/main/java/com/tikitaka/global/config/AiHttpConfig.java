package com.tikitaka.global.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiHttpConfig {

    @Bean
    @Qualifier("aiRestClient")
    public RestClient aiRestClient(
            RestClient.Builder builder,
            @Value("${ai.base-url:http://localhost:8000}")
            String aiBaseUrl,
            @Value("${ai.connect-timeout-ms:3000}")
            int connectTimeoutMillis,
            @Value("${ai.read-timeout-ms:120000}")
            int readTimeoutMillis
    ) {
        if (connectTimeoutMillis <= 0) {
            throw new IllegalArgumentException(
                    "AI connect timeout must be positive."
            );
        }

        if (readTimeoutMillis <= 0) {
            throw new IllegalArgumentException(
                    "AI read timeout must be positive."
            );
        }

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(
                connectTimeoutMillis
        );

        requestFactory.setReadTimeout(
                readTimeoutMillis
        );

        return builder
                .baseUrl(aiBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
