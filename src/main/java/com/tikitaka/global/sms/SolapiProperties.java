package com.tikitaka.global.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Validated
@ConfigurationProperties(prefix = "solapi")
public record SolapiProperties(
        @NotBlank String apiKey,
        @NotBlank String apiSecret,
        @NotBlank @Pattern(regexp = "^[0-9]+$") String senderNumber
) {
}