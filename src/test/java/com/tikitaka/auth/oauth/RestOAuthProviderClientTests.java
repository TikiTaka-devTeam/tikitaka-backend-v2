package com.tikitaka.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class RestOAuthProviderClientTests {

    @Test
    void ignoresKakaoDefaultProfileImage() {
        Object image = RestOAuthProviderClient.kakaoProfileImage(Map.of(
                "profile_image_url", "https://example.com/default-profile.jpeg",
                "is_default_image", true));

        assertThat(image).isNull();
    }

    @Test
    void usesKakaoCustomProfileImage() {
        Object image = RestOAuthProviderClient.kakaoProfileImage(Map.of(
                "profile_image_url", "https://example.com/custom-profile.jpeg",
                "is_default_image", false));

        assertThat(image).isEqualTo("https://example.com/custom-profile.jpeg");
    }

    @Test
    void usesProfileImageWhenDefaultFlagIsMissing() {
        Object image = RestOAuthProviderClient.kakaoProfileImage(Map.of(
                "profile_image_url", "https://example.com/profile.jpeg"));

        assertThat(image).isEqualTo("https://example.com/profile.jpeg");
    }
}