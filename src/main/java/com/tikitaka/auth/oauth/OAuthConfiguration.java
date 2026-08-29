package com.tikitaka.auth.oauth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OAuthProperties.class)
public class OAuthConfiguration {
}
