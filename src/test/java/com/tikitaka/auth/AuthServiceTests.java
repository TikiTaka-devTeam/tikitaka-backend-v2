package com.tikitaka.auth;

import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.auth.service.PhoneVerificationConsumer;
import com.tikitaka.auth.service.AuthService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.tikitaka.auth.dto.LoginRequest;
import com.tikitaka.auth.dto.LoginResponse;
import com.tikitaka.auth.dto.SignupRequest;
import com.tikitaka.auth.dto.TokenResponse;
import com.tikitaka.auth.entity.Token;
import com.tikitaka.auth.repository.TokenRepository;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.s3.S3Service;
import com.tikitaka.global.security.JwtProvider;
import com.tikitaka.global.security.RefreshTokenHasher;
import com.tikitaka.global.security.TokenPair;
import com.tikitaka.user.entity.AccountType;
import com.tikitaka.user.entity.User;
import com.tikitaka.user.entity.UserStatus;
import com.tikitaka.user.repository.UserRepository;

class AuthServiceTests {
    private static final Instant NOW = Instant.parse("2026-08-24T05:00:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final TokenRepository tokenRepository = mock(TokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final RefreshTokenHasher refreshTokenHasher = mock(RefreshTokenHasher.class);
    private final PhoneVerificationConsumer phoneVerificationConsumer = mock(PhoneVerificationConsumer.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<S3Service> s3Provider = mock(ObjectProvider.class);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                tokenRepository,
                passwordEncoder,
                jwtProvider,
                refreshTokenHasher,
                phoneVerificationConsumer,
                s3Provider,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void checksAvailabilityWithNormalizedValues() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("01012345678")).thenReturn(true);

        assertThat(authService.checkEmail(" User@Example.COM ").available()).isTrue();
        assertThat(authService.checkPhone("010-1234-5678").available()).isFalse();
    }

    @Test
    void signsUpVerifiedPhoneUserWithEncodedPassword() {
        SignupRequest request = new SignupRequest(
                "User@Example.com",
                "Test1234!",
                "???",
                "010-1234-5678",
                "verification-token",
                AccountType.STUDENT,
                "?????",
                "??????",
                "20231370");
        when(passwordEncoder.encode("Test1234!")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.signup(request, null);

        verify(phoneVerificationConsumer).consume("verification-token", "01012345678");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(userCaptor.getValue().getPhoneNumber()).isEqualTo("01012345678");
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("encoded-password");
    }

    @Test
    void loginIssuesTokensAndStoresOnlyRefreshTokenHash() {
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId);
        TokenPair pair = new TokenPair(
                "access-token",
                NOW.plusSeconds(900),
                "refresh-token",
                NOW.plusSeconds(1200));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Test1234!", "encoded-password")).thenReturn(true);
        when(jwtProvider.issue(userId)).thenReturn(pair);
        when(refreshTokenHasher.hash("refresh-token")).thenReturn("refresh-hash");

        LoginResponse response = authService.login(new LoginRequest("User@Example.com", "Test1234!"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        ArgumentCaptor<Token> tokenCaptor = ArgumentCaptor.forClass(Token.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getRefreshTokenHash()).isEqualTo("refresh-hash");
    }

    @Test
    void refreshRevokesOldTokenAndRotatesTokenPair() {
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId);
        Token storedToken = mock(Token.class);
        TokenPair newPair = new TokenPair(
                "new-access",
                NOW.plusSeconds(900),
                "new-refresh",
                NOW.plusSeconds(1200));
        when(jwtProvider.validateRefreshToken("old-refresh")).thenReturn(userId);
        when(refreshTokenHasher.hash("old-refresh")).thenReturn("old-hash");
        when(tokenRepository.findForUpdateByRefreshTokenHash("old-hash"))
                .thenReturn(Optional.of(storedToken));
        when(storedToken.isUsable(NOW)).thenReturn(true);
        when(storedToken.getUser()).thenReturn(user);
        when(jwtProvider.issue(userId)).thenReturn(newPair);
        when(refreshTokenHasher.hash("new-refresh")).thenReturn("new-hash");

        TokenResponse response = authService.refresh("old-refresh");

        verify(storedToken).revoke(NOW);
        verify(tokenRepository).save(any(Token.class));
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void logoutRejectsRefreshTokenOwnedByAnotherUser() {
        when(jwtProvider.validateRefreshToken("refresh-token")).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> authService.logout(UUID.randomUUID(), "refresh-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
        verify(tokenRepository, never()).findForUpdateByRefreshTokenHash(any());
    }

    private User activeUser(UUID userId) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.getPassword()).thenReturn("encoded-password");
        when(user.getName()).thenReturn("???");
        when(user.getAccountType()).thenReturn(AccountType.STUDENT);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        return user;
    }
}
