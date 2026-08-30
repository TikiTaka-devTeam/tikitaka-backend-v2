package com.tikitaka.auth.dto;

import java.util.UUID;

import com.tikitaka.user.entity.AccountType;
import com.tikitaka.user.entity.User;

import io.swagger.v3.oas.annotations.media.Schema;

public record SignupResponse(
        @Schema(description = "사용자 ID", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID userId,
        @Schema(description = "이메일", example = "32221234@dankook.ac.kr")
        String email,
        @Schema(description = "이름", example = "이학생")
        String name,
        @Schema(description = "휴대폰 번호", example = "010-1234-1234")
        String phoneNumber,
        @Schema(description = "계정 유형", example = "PROFESSOR")
        AccountType accountType,
        @Schema(description = "대학교", example = "단국대학교")
        String univ,
        @Schema(description = "전공", example = "컴퓨터공학")
        String major,
        @Schema(description = "학번 또는 교번", example = "32221234")
        String memberIdNumber,
        @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.jpg")
        String profileUrl
) {
    public static SignupResponse from(User user) {
        return new SignupResponse(
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
