package com.tikitaka.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "swagger.auth")
public record SwaggerAuthProperties(String username, String password) {
    @Override
    public String toString() {
        return "SwaggerAuthProperties[credentials redacted]";
    }
}
