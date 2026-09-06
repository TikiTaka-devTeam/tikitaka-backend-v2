package com.tikitaka.assignment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AssignmentSubmissionDownloadResponse(

        @JsonProperty("download_url")
        String downloadUrl

) {
}