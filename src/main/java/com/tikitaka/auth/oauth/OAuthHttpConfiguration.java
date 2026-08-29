package com.tikitaka.auth.oauth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OAuthHttpConfiguration {
    @Bean
    RestClient.Builder oauthRestClientBuilder() {
        return RestClient.builder();
    }
}
