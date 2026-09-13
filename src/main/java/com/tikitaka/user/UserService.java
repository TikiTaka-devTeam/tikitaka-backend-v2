package com.tikitaka.user;

import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.s3.S3Service;
import com.tikitaka.global.s3.S3UploadResult;
import com.tikitaka.user.dto.ProfileImageResponse;
import com.tikitaka.user.dto.PasswordChangeRequest;
import com.tikitaka.user.dto.UserProfileResponse;
import com.tikitaka.user.entity.User;
import com.tikitaka.user.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<S3Service> s3ServiceProvider;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            ObjectProvider<S3Service> s3ServiceProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.s3ServiceProvider = s3ServiceProvider;
    }

    public UserProfileResponse getMyProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        return UserProfileResponse.from(user, profileImageUrl(user.getProfileUrl()));
    }

    @Transactional
    public ProfileImageResponse updateProfileImage(UUID userId, MultipartFile profileImage, boolean delete) {
        boolean hasFile = profileImage != null;
        if (hasFile == delete) {
            throw new BusinessException(UserErrorCode.INVALID_PROFILE_IMAGE_REQUEST);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        String previousUrl = user.getProfileUrl();
        S3Service s3Service = s3ServiceProvider.getIfAvailable();

        if (delete) {
            if (s3Service != null) s3Service.deleteByUrlIfManaged(previousUrl);
            user.changeProfileImage(null);
            return new ProfileImageResponse(null);
        }
        if (s3Service == null) {
            throw new BusinessException(UserErrorCode.PROFILE_IMAGE_UNAVAILABLE);
        }

        S3UploadResult uploaded = s3Service.uploadProfileImage(profileImage, user.getName());
        try {
            s3Service.deleteByUrlIfManaged(previousUrl);
        } catch (BusinessException exception) {
            try {
                s3Service.delete(uploaded.key());
            } catch (BusinessException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
        user.changeProfileImage(uploaded.url());
        return new ProfileImageResponse(profileImageUrl(uploaded.url()));
    }

    @Transactional
    public void changePassword(UUID userId, PasswordChangeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        if (user.getPassword() == null) throw new BusinessException(UserErrorCode.PASSWORD_NOT_REGISTERED);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException(UserErrorCode.INVALID_CURRENT_PASSWORD);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException(UserErrorCode.SAME_PASSWORD);
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    private String profileImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        S3Service s3Service = s3ServiceProvider.getIfAvailable();
        return s3Service == null ? url : s3Service.presignedProfileUrl(url);
    }
}
