package com.tikitaka.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordChangeRequest(
        @NotBlank String currentPassword,
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s]).{8,64}$",
                message = "비밀번호는 8~64자이며 영문, 숫자, 특수문자를 포함해야 합니다."
        )
        String newPassword
) {
}
