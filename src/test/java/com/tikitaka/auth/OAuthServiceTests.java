package com.tikitaka.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.tikitaka.auth.dto.OAuthLoginResponse;
import com.tikitaka.auth.dto.OAuthSignupRequest;
import com.tikitaka.auth.dto.OAuthSignupResponse;
import com.tikitaka.auth.entity.Auth;
import com.tikitaka.auth.entity.AuthProvider;
import com.tikitaka.auth.oauth.OAuthProfile;
import com.tikitaka.auth.oauth.OAuthProviderClient;
import com.tikitaka.auth.oauth.OAuthSignupClaims;
import com.tikitaka.auth.oauth.OAuthSignupTokenService;
import com.tikitaka.auth.repository.AuthRepository;
import com.tikitaka.auth.repository.TokenRepository;
import com.tikitaka.auth.service.OAuthService;
import com.tikitaka.auth.service.PhoneVerificationConsumer;
import com.tikitaka.global.security.JwtProvider;
import com.tikitaka.global.security.RefreshTokenHasher;
import com.tikitaka.global.security.TokenPair;
import com.tikitaka.user.entity.AccountType;
import com.tikitaka.user.entity.User;
import com.tikitaka.user.entity.UserStatus;
import com.tikitaka.user.repository.UserRepository;

class OAuthServiceTests {
    private final OAuthProviderClient providerClient = mock(OAuthProviderClient.class);
    private final OAuthSignupTokenService signupTokens = mock(OAuthSignupTokenService.class);
    private final AuthRepository authRepository = mock(AuthRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TokenRepository tokenRepository = mock(TokenRepository.class);
    private final PhoneVerificationConsumer phoneConsumer = mock(PhoneVerificationConsumer.class);
    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final RefreshTokenHasher refreshHasher = mock(RefreshTokenHasher.class);
    private OAuthService service;

    @BeforeEach
    void setUp() {
        service = new OAuthService(providerClient, signupTokens, authRepository, userRepository,
                tokenRepository, phoneConsumer, jwtProvider, refreshHasher);
    }

    @Test
    void existingOAuthAccountLogsInAndStoresRefreshTokenHash() {
        User user = mock(User.class);
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);
        when(user.getName()).thenReturn("김선민");
        when(user.getAccountType()).thenReturn(AccountType.STUDENT);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        OAuthProfile profile = new OAuthProfile(AuthProvider.GOOGLE, "provider-id",
                "User@Example.com", "김선민", null);
        when(providerClient.fetchProfile(AuthProvider.GOOGLE, "code")).thenReturn(profile);
        Auth auth = mockAuth(user);
        when(authRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "provider-id"))
                .thenReturn(Optional.of(auth));
        when(jwtProvider.issue(userId)).thenReturn(tokens());
        when(refreshHasher.hash("refresh")).thenReturn("refresh-hash");

        OAuthLoginResponse response = service.authorize("google", "code");

        assertThat(response.signupRequired()).isFalse();
        assertThat(response.accessToken()).isEqualTo("access");
        verify(tokenRepository).save(any());
    }

    @Test
    void unknownOAuthAccountReturnsNormalizedProfileAndSignupToken() {
        OAuthProfile profile = new OAuthProfile(AuthProvider.KAKAO, "provider-id",
                " User@Example.COM ", "김선민", "https://profile");
        when(providerClient.fetchProfile(AuthProvider.KAKAO, "code")).thenReturn(profile);
        when(authRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "provider-id"))
                .thenReturn(Optional.empty());
        when(signupTokens.issue(profile)).thenReturn("signup-token");

        OAuthLoginResponse response = service.authorize("kakao", "code");

        assertThat(response.signupRequired()).isTrue();
        assertThat(response.signupToken()).isEqualTo("signup-token");
        assertThat(response.oauthProfile().email()).isEqualTo("user@example.com");
    }

    @Test
    void socialSignupConsumesVerifiedPhoneAndCreatesProviderAccount() {
        OAuthSignupRequest request = new OAuthSignupRequest("signup-token", "010-1234-5678",
                "phone-token", AccountType.STUDENT, "단국대학교", "컴퓨터공학과", "20231370");
        when(signupTokens.validate("signup-token")).thenReturn(new OAuthSignupClaims(
                AuthProvider.GOOGLE, "provider-id", "User@Example.com", "김선민", "https://profile"));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtProvider.issue(null)).thenReturn(tokens());
        when(refreshHasher.hash("refresh")).thenReturn("refresh-hash");

        OAuthSignupResponse response = service.signup(request);

        verify(phoneConsumer).consume("phone-token", "01012345678");
        verify(authRepository).save(any(Auth.class));
        assertThat(response.user().email()).isEqualTo("user@example.com");
        assertThat(response.user().phoneNumber()).isEqualTo("01012345678");
    }

    private Auth mockAuth(User user) {
        Auth auth = mock(Auth.class);
        when(auth.getUser()).thenReturn(user);
        return auth;
    }

    private TokenPair tokens() {
        return new TokenPair("access", Instant.now().plusSeconds(60), "refresh",
                Instant.now().plusSeconds(120));
    }
}
