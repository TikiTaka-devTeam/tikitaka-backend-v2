package com.tikitaka.user.dto;

import java.util.UUID;

import com.tikitaka.user.entity.AccountType;
import com.tikitaka.user.entity.User;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserProfileResponse(
        @Schema(description = "사용자 ID") UUID userId,
        @Schema(description = "이메일") String email,
        @Schema(description = "이름") String name,
        @Schema(description = "휴대폰 번호") String phoneNumber,
        @Schema(description = "계정 유형") AccountType accountType,
        @Schema(description = "대학교") String univ,
        @Schema(description = "전공") String major,
        @Schema(description = "학번 또는 교번") String memberIdNumber,
        @Schema(description = "프로필 이미지 URL", nullable = true) String profileUrl
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPhoneNumber(),
                user.getAccountType(),
                user.getUniv(),
                user.getMajor(),
                user.getMemberIdNumber(),
                user.getProfileUrl());
    }
}