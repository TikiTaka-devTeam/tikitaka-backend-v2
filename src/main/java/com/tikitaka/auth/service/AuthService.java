package com.tikitaka.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.auth.dto.EmailAvailabilityResponse;
import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.auth.dto.LoginRequest;
import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.auth.dto.LoginResponse;
import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.auth.dto.PhoneAvailabilityResponse;
import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.auth.dto.SignupRequest;
import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.auth.dto.SignupResponse;
import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.auth.dto.TokenResponse;
import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.auth.entity.Token;
import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.auth.repository.TokenRepository;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.s3.FileUploadType;
import com.tikitaka.global.s3.S3Service;
import com.tikitaka.global.security.JwtProvider;
import com.tikitaka.global.security.RefreshTokenHasher;
import com.tikitaka.global.security.TokenPair;
import com.tikitaka.user.entity.User;
import com.tikitaka.user.entity.UserStatus;
import com.tikitaka.user.repository.UserRepository;

import io.jsonwebtoken.JwtException;

@Service
@Transactional(readOnly = true)
public class AuthService {
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenHasher refreshTokenHasher;
    private final PhoneVerificationConsumer phoneVerificationConsumer;
    private final ObjectProvider<S3Service> s3ServiceProvider;
    private final Clock clock;

    public AuthService(
            UserRepository userRepository,
            TokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            RefreshTokenHasher refreshTokenHasher,
            PhoneVerificationConsumer phoneVerificationConsumer,
            ObjectProvider<S3Service> s3ServiceProvider,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokenHasher = refreshTokenHasher;
        this.phoneVerificationConsumer = phoneVerificationConsumer;
        this.s3ServiceProvider = s3ServiceProvider;
        this.clock = clock;
    }

    public EmailAvailabilityResponse checkEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        return new EmailAvailabilityResponse(normalizedEmail, !userRepository.existsByEmail(normalizedEmail));
    }

    public PhoneAvailabilityResponse checkPhone(String phoneNumber) {
        String normalizedPhone = normalizePhone(phoneNumber);
        return new PhoneAvailabilityResponse(normalizedPhone, !userRepository.existsByPhoneNumber(normalizedPhone));
    }

    @Transactional
    public SignupResponse signup(SignupRequest request, MultipartFile profileImage) {
        String email = normalizeEmail(request.email());
        String phoneNumber = normalizePhone(request.phoneNumber());
        ensureEmailAvailable(email);
        ensurePhoneAvailable(phoneNumber);
        phoneVerificationConsumer.consume(request.phoneVerificationToken(), phoneNumber);

        String profileUrl = uploadProfileImage(profileImage);
        User user = User.createLocal(
                email,
                passwordEncoder.encode(request.password()),
                request.name().trim(),
                request.accountType(),
                phoneNumber,
                request.univ().trim(),
                request.major().trim(),
                request.memberIdNumber().trim(),
                profileUrl);
        return SignupResponse.from(userRepository.save(user));
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));
        if (user.getPassword() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_UNAVAILABLE);
        }

        TokenPair tokenPair = issueAndStore(user);
        return LoginResponse.of(tokenPair, user);
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        UUID tokenUserId = validateRefreshToken(refreshToken);
        String tokenHash = refreshTokenHasher.hash(refreshToken);
        Token storedToken = tokenRepository.findForUpdateByRefreshTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        Instant now = clock.instant();
        if (!storedToken.isUsable(now) || !storedToken.getUser().getId().equals(tokenUserId)) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (storedToken.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_UNAVAILABLE);
        }

        storedToken.revoke(now);
        return TokenResponse.from(issueAndStore(storedToken.getUser()));
    }

    @Transactional
    public void logout(UUID authenticatedUserId, String refreshToken) {
        UUID tokenUserId = validateRefreshToken(refreshToken);
        if (!authenticatedUserId.equals(tokenUserId)) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        Token storedToken = tokenRepository.findForUpdateByRefreshTokenHash(
                        refreshTokenHasher.hash(refreshToken))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        if (!storedToken.isUsable(clock.instant())) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        storedToken.revoke(clock.instant());
    }

    private TokenPair issueAndStore(User user) {
        TokenPair pair = jwtProvider.issue(user.getId());
        tokenRepository.save(Token.create(
                user,
                refreshTokenHasher.hash(pair.refreshToken()),
                pair.refreshTokenExpiresAt()));
        return pair;
    }

    private UUID validateRefreshToken(String refreshToken) {
        try {
            return jwtProvider.validateRefreshToken(refreshToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN, exception);
        }
    }

    private String uploadProfileImage(MultipartFile profileImage) {
        if (profileImage == null || profileImage.isEmpty()) {
            return null;
        }
        S3Service s3Service = s3ServiceProvider.getIfAvailable();
        if (s3Service == null) {
            throw new BusinessException(AuthErrorCode.PROFILE_UPLOAD_UNAVAILABLE);
        }
        return s3Service.upload(profileImage, "profiles", FileUploadType.PROFILE_IMAGE).url();
    }

    private void ensureEmailAvailable(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    private void ensurePhoneAvailable(String phoneNumber) {
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new BusinessException(AuthErrorCode.PHONE_ALREADY_EXISTS);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String phoneNumber) {
        return phoneNumber.replace("-", "").trim();
    }
}
