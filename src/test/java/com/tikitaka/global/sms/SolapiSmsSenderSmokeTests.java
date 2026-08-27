package com.tikitaka.global.sms;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.solapi.sdk.SolapiClient;

class SolapiSmsSenderSmokeTests {

    @Test
    @EnabledIfSystemProperty(named = "solapi.smoke-test", matches = "true")
    void sendsRealSmsUsingEnvironmentCredentials() {
        String apiKey = requiredEnvironmentVariable("SOLAPI_API_KEY");
        String apiSecret = requiredEnvironmentVariable("SOLAPI_API_SECRET");
        String senderNumber = requiredEnvironmentVariable("SOLAPI_SENDER_NUMBER");
        String recipientNumber = requiredEnvironmentVariable("SOLAPI_TEST_RECIPIENT");
        SolapiSmsSender sender = new SolapiSmsSender(
                SolapiClient.INSTANCE.createInstance(apiKey, apiSecret),
                new SolapiProperties(apiKey, apiSecret, senderNumber));

        assertDoesNotThrow(() -> sender.send(
                recipientNumber,
                "[Tikitaka] SOLAPI 문자 발송 테스트입니다."));
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required");
        }
        return value.trim();
    }
}
