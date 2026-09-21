package com.tikitaka.question.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(
        @NotBlank
        @Size(max = 100)
        String name
) {
}
