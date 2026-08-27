package com.tikitaka.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PhoneVerificationHasher {
    // 인증번호 원문을 저장하지 않고 서버 비밀 pepper와 함께 HMAC 처리합니다.
    // pepper는 환경변수로만 관리하며 외부에 노출하지 않습니다.
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String DIGEST_ALGORITHM = "SHA-256";
    private final byte[] pepper;

    public PhoneVerificationHasher(
            @Value("${security.phone-verification.pepper}") String pepper
    ) {
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalArgumentException("Phone verification pepper must not be blank");
        }
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    public String hashCode(String phoneNumber, String verificationCode) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(pepper, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(
                    (phoneNumber + ":" + verificationCode).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to hash phone verification code", exception);
        }
    }

    public boolean matchesCode(String phoneNumber, String verificationCode, String storedHash) {
        return matches(hashCode(phoneNumber, verificationCode), storedHash);
    }

    public String hashToken(String verificationToken) {
        try {
            byte[] hash = MessageDigest.getInstance(DIGEST_ALGORITHM)
                    .digest(verificationToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to hash phone verification token", exception);
        }
    }

    public boolean matchesToken(String verificationToken, String storedHash) {
        return matches(hashToken(verificationToken), storedHash);
    }

    private boolean matches(String candidateHash, String storedHash) {
        if (storedHash == null || storedHash.length() != candidateHash.length()) {
            return false;
        }
        return MessageDigest.isEqual(
                candidateHash.getBytes(StandardCharsets.US_ASCII),
                storedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
