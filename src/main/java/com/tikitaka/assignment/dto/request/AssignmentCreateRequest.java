package com.tikitaka.assignment.dto.request;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssignmentCreateRequest(
        @NotBlank
        @Size(max = 255)
        String title,

        @NotBlank
        String description,

        @NotNull
        Instant dueAt,

        @NotNull
        CloseType closeType
) {
    public enum CloseType {
        AUTO,
        MANUAL
    }

    public boolean autoClose() {
        return closeType == CloseType.AUTO;
    }
}
