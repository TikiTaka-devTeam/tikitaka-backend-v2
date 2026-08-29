package com.tikitaka.inquiry.dto;

import com.tikitaka.inquiry.entity.InquiryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InquiryCreateRequest(
        @NotNull InquiryType type,
        @NotBlank @Size(max = 255) String title,
        @NotBlank String content
) {
}
