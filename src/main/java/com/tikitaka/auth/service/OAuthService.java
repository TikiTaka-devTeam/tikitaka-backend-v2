package com.tikitaka.auth.service;

import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.tikitaka.auth.dto.OAuthLoginResponse;
import com.tikitaka.auth.dto.OAuthProfileResponse;
import com.tikitaka.auth.dto.OAuthSignupRequest;
import com.tikitaka.auth.dto.OAuthSignupResponse;
import com.tikitaka.auth.entity.Auth;
import com.tikitaka.auth.entity.AuthProvider;
import com.tikitaka.auth.entity.Token;
import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.auth.oauth.OAuthErrorCode;
import com.tikitaka.auth.oauth.OAuthProfile;
import com.tikitaka.auth.oauth.OAuthProviderClient;
import com.tikitaka.auth.oauth.OAuthSignupClaims;
import com.tikitaka.auth.oauth.OAuthSignupTokenService;
import com.tikitaka.auth.repository.AuthRepository;
import com.tikitaka.auth.repository.TokenRepository;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.security.JwtProvider;
import com.tikitaka.global.security.RefreshTokenHasher;
import com.tikitaka.global.security.TokenPair;
import com.tikitaka.user.entity.User;
import com.tikitaka.user.entity.UserStatus;
import com.tikitaka.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;

@Service
@Transactional(readOnly = true)
public class OAuthService {
    private final OAuthProviderClient providerClient;
    private final OAuthSignupTokenService signupTokens;
    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final PhoneVerificationConsumer phoneVerificationConsumer;
    private final JwtProvider jwtProvider;
    private final RefreshTokenHasher refreshTokenHasher;

    public OAuthService(OAuthProviderClient providerClient, OAuthSignupTokenService signupTokens,
            AuthRepository authRepository, UserRepository userRepository, TokenRepository tokenRepository,
            PhoneVerificationConsumer phoneVerificationConsumer, JwtProvider jwtProvider,
            RefreshTokenHasher refreshTokenHasher) {
        this.providerClient = providerClient;
        this.signupTokens = signupTokens;
        this.authRepository = authRepository;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.phoneVerificationConsumer = phoneVerificationConsumer;
        this.jwtProvider = jwtProvider;
        this.refreshTokenHasher = refreshTokenHasher;
    }

    @Transactional
    public OAuthLoginResponse authorize(String providerValue, String authorizationCode) {
        AuthProvider provider = parseProvider(providerValue);
        OAuthProfile profile = providerClient.fetchProfile(provider, authorizationCode);
        return authRepository.findByProviderAndProviderUserId(provider, profile.providerUserId())
                .map(Auth::getUser).map(user -> {
                    ensureActive(user);
                    return OAuthLoginResponse.login(issueTokens(user), user);
                }).orElseGet(() -> OAuthLoginResponse.signup(signupTokens.issue(profile),
                        new OAuthProfileResponse(normalizeEmail(profile.email()), profile.name(), profile.profileUrl())));
    }

    @Transactional
    public OAuthSignupResponse signup(OAuthSignupRequest request) {
        OAuthSignupClaims claims = validateSignupToken(request.signupToken());
        String email = normalizeEmail(claims.email());
        String phone = request.phoneNumber().replace("-", "").trim();
        if (authRepository.existsByProviderAndProviderUserId(claims.provider(), claims.providerUserId())) {
            throw new BusinessException(OAuthErrorCode.ACCOUNT_ALREADY_REGISTERED);
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (userRepository.existsByPhoneNumber(phone)) {
            throw new BusinessException(AuthErrorCode.PHONE_NUMBER_ALREADY_REGISTERED);
        }
        phoneVerificationConsumer.consume(request.phoneVerificationToken(), phone);
        User user = userRepository.save(User.createLocal(email, null, claims.name().trim(), request.accountType(),
                phone, request.univ().trim(), request.major().trim(), request.memberIdNumber().trim(),
                claims.profileUrl()));
        authRepository.save(Auth.create(user, claims.provider(), claims.providerUserId()));
        return OAuthSignupResponse.of(issueTokens(user), user);
    }

    private TokenPair issueTokens(User user) {
        TokenPair pair = jwtProvider.issue(user.getId());
        tokenRepository.save(Token.create(user, refreshTokenHasher.hash(pair.refreshToken()),
                pair.refreshTokenExpiresAt()));
        return pair;
    }

    private OAuthSignupClaims validateSignupToken(String token) {
        try {
            OAuthSignupClaims claims = signupTokens.validate(token);
            if (claims.providerUserId() == null || claims.email() == null || claims.name() == null) {
                throw new BusinessException(OAuthErrorCode.SIGNUP_TOKEN_INVALID);
            }
            return claims;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(OAuthErrorCode.SIGNUP_TOKEN_INVALID, exception);
        }
    }

    private AuthProvider parseProvider(String value) {
        try {
            return AuthProvider.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(OAuthErrorCode.PROVIDER_UNSUPPORTED, exception);
        }
    }

    private void ensureActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_UNAVAILABLE);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
