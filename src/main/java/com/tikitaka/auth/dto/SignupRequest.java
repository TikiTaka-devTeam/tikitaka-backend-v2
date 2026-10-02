package com.tikitaka.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.user.entity.AccountType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Schema(description = "이메일", example = "32221234@dankook.ac.kr")
        @NotBlank @Email @Size(max = 100) String email,
        @Schema(description = "영문, 숫자, 특수문자를 포함한 비밀번호", example = "Student1234!")
        @NotBlank
        @Size(min = 8, max = 64)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$")
        String password,
        @Schema(description = "이름", example = "이학생")
        @NotBlank @Size(max = 30) String name,
        @Schema(description = "휴대폰 번호", example = "010-1234-1234")
        @JsonProperty("phone_number")
        @NotBlank @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$") String phoneNumber,
        @Schema(description = "휴대폰 인증 완료 토큰", example = "phone-verification-token")
        @JsonProperty("phone_verification_token") @NotBlank String phoneVerificationToken,
        @Schema(description = "계정 유형", example = "PROFESSOR", allowableValues = {"STUDENT", "PROFESSOR"})
        @JsonProperty("account_type") @NotNull AccountType accountType,
        @Schema(description = "대학교", example = "단국대학교")
        @NotBlank @Size(max = 100) String univ,
        @Schema(description = "전공", example = "컴퓨터공학")
        @NotBlank @Size(max = 100) String major,
        @Schema(description = "학번 또는 교번 (선택, 생략 또는 null 허용)", example = "32221234")
        @JsonProperty("member_id_number") @Size(max = 30) String memberIdNumber
) {
}
