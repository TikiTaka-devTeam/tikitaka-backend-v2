package com.tikitaka.global.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

@Component
public class RefreshTokenHasher {
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] pepper;

    public RefreshTokenHasher(JwtProperties properties) {
        if (properties.refreshTokenPepper() == null || properties.refreshTokenPepper().isBlank()) {
            throw new IllegalStateException("security.jwt.refresh-token-pepper must be configured");
        }
        this.pepper = properties.refreshTokenPepper().getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken must not be blank");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(pepper, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(refreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Refresh token hashing is unavailable", exception);
        }
    }

    public boolean matches(String refreshToken, String expectedHash) {
        if (refreshToken == null || refreshToken.isBlank() || expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(refreshToken).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
