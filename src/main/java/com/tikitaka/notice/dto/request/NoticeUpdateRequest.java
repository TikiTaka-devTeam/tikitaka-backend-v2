package com.tikitaka.notice.dto.request;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoticeUpdateRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank String content,
        @JsonProperty("retained_file_ids") List<UUID> retainedFileIds
) {}
