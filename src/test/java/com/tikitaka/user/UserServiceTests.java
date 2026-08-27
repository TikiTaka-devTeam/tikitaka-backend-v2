package com.tikitaka.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.user.dto.PasswordChangeRequest;
import com.tikitaka.user.entity.AccountType;
import com.tikitaka.user.entity.User;
import com.tikitaka.user.repository.UserRepository;

class UserServiceTests {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserService userService = new UserService(userRepository, passwordEncoder);

    @Test
    void changesPasswordAfterCheckingCurrentPassword() {
        UUID userId = UUID.randomUUID();
        User user = localUser("encoded-current");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Current123!", "encoded-current")).thenReturn(true);
        when(passwordEncoder.matches("NewPassword123!", "encoded-current")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword123!")).thenReturn("encoded-new");

        userService.changePassword(userId, new PasswordChangeRequest("Current123!", "NewPassword123!"));

        assertThat(user.getPassword()).isEqualTo("encoded-new");
        verify(passwordEncoder).encode("NewPassword123!");
    }

    @Test
    void rejectsIncorrectCurrentPassword() {
        UUID userId = UUID.randomUUID();
        User user = localUser("encoded-current");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Wrong123!", "encoded-current")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(
                userId, new PasswordChangeRequest("Wrong123!", "NewPassword123!")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.INVALID_CURRENT_PASSWORD);
    }

    @Test
    void rejectsOAuthOnlyAccountWithoutPassword() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(localUser(null)));

        assertThatThrownBy(() -> userService.changePassword(
                userId, new PasswordChangeRequest("Current123!", "NewPassword123!")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.PASSWORD_NOT_REGISTERED);
    }

    private User localUser(String password) {
        return User.createLocal(
                "student@example.com", password, "김선민", AccountType.STUDENT,
                "01012345678", "단국대학교", "컴퓨터공학과", "20231370", null);
    }
}
