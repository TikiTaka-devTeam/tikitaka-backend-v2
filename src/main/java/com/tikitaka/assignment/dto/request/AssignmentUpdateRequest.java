package com.tikitaka.assignment.dto.request;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssignmentUpdateRequest(

        @JsonProperty("title")
        @NotBlank
        @Size(max = 255)
        String title,

        @JsonProperty("description")
        @NotBlank
        String description,

        @JsonProperty("due_at")
        @NotNull
        Instant dueAt,

        @JsonProperty("close_type")
        @NotNull
        CloseType closeType,

        @JsonProperty("retained_file_ids")
        List<UUID> retainedFileIds

) {

    public enum CloseType {
        AUTO,
        MANUAL
    }

    public boolean autoClose() {
        return closeType == CloseType.AUTO;
    }
}