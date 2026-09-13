package com.tikitaka.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.s3.S3Service;
import com.tikitaka.global.s3.S3UploadResult;
import com.tikitaka.user.dto.ProfileImageResponse;
import com.tikitaka.user.dto.PasswordChangeRequest;
import com.tikitaka.user.dto.UserProfileResponse;
import com.tikitaka.user.entity.AccountType;
import com.tikitaka.user.entity.User;
import com.tikitaka.user.repository.UserRepository;

class UserServiceTests {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<S3Service> s3ServiceProvider = mock(ObjectProvider.class);
    private final S3Service s3Service = mock(S3Service.class);
    private final UserService userService = new UserService(userRepository, passwordEncoder, s3ServiceProvider);

    @Test
    void uploadsAndReplacesProfileImage() {
        UUID userId = UUID.randomUUID();
        User user = localUser("encoded-password");
        user.changeProfileImage("https://test-bucket.s3.ap-northeast-2.amazonaws.com/profiles/old.png");
        MockMultipartFile image = new MockMultipartFile(
                "profile_image", "new.png", "image/png", new byte[] {1});
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(s3ServiceProvider.getIfAvailable()).thenReturn(s3Service);
        when(s3Service.uploadProfileImage(image, user.getName()))
                .thenReturn(new S3UploadResult("profiles/new.png", "https://example.com/profiles/new.png"));

        when(s3Service.presignedProfileUrl("https://example.com/profiles/new.png"))
                .thenReturn("https://signed.example/profiles/new.png");
        ProfileImageResponse response = userService.updateProfileImage(userId, image, false);

        assertThat(response.profileUrl()).isEqualTo("https://signed.example/profiles/new.png");
        assertThat(user.getProfileUrl()).isEqualTo("https://example.com/profiles/new.png");
        verify(s3Service).deleteByUrlIfManaged(
                "https://test-bucket.s3.ap-northeast-2.amazonaws.com/profiles/old.png");
    }

    @Test
    void deletesProfileImageIdempotently() {
        UUID userId = UUID.randomUUID();
        User user = localUser("encoded-password");
        user.changeProfileImage("https://example.com/profiles/old.png");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(s3ServiceProvider.getIfAvailable()).thenReturn(s3Service);

        ProfileImageResponse response = userService.updateProfileImage(userId, null, true);

        assertThat(response.profileUrl()).isNull();
        assertThat(user.getProfileUrl()).isNull();
        verify(s3Service).deleteByUrlIfManaged("https://example.com/profiles/old.png");
    }

    @Test
    void rejectsAmbiguousProfileImageRequest() {
        MockMultipartFile image = new MockMultipartFile(
                "profile_image", "new.png", "image/png", new byte[] {1});

        assertThatThrownBy(() -> userService.updateProfileImage(UUID.randomUUID(), image, true))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.INVALID_PROFILE_IMAGE_REQUEST);
        assertThatThrownBy(() -> userService.updateProfileImage(UUID.randomUUID(), null, false))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.INVALID_PROFILE_IMAGE_REQUEST);
    }

    @Test
    void returnsAuthenticatedUsersProfile() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getEmail()).thenReturn("student@example.com");
        when(user.getName()).thenReturn("김선민");
        when(user.getPhoneNumber()).thenReturn("01012345678");
        when(user.getAccountType()).thenReturn(AccountType.STUDENT);
        when(user.getUniv()).thenReturn("단국대학교");
        when(user.getMajor()).thenReturn("컴퓨터공학과");
        when(user.getMemberIdNumber()).thenReturn("20231370");
        when(user.getProfileUrl()).thenReturn("https://example.com/profile.jpg");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.getMyProfile(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("student@example.com");
        assertThat(response.name()).isEqualTo("김선민");
        assertThat(response.phoneNumber()).isEqualTo("01012345678");
        assertThat(response.accountType()).isEqualTo(AccountType.STUDENT);
        assertThat(response.univ()).isEqualTo("단국대학교");
        assertThat(response.major()).isEqualTo("컴퓨터공학과");
        assertThat(response.memberIdNumber()).isEqualTo("20231370");
        assertThat(response.profileUrl()).isEqualTo("https://example.com/profile.jpg");
    }

    @Test
    void resolvesExistingProfileImageForDisplayWithoutChangingStoredUrl() {
        UUID userId = UUID.randomUUID();
        User user = localUser("encoded-password");
        String stored = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/profiles/old.png";
        user.changeProfileImage(stored);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(s3ServiceProvider.getIfAvailable()).thenReturn(s3Service);
        when(s3Service.presignedProfileUrl(stored)).thenReturn("https://signed.example/old.png");

        assertThat(userService.getMyProfile(userId).profileUrl()).isEqualTo("https://signed.example/old.png");
        assertThat(user.getProfileUrl()).isEqualTo(stored);
    }

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
