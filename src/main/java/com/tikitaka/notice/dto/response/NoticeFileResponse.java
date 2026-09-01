package com.tikitaka.notice.dto.response;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tikitaka.notice.entity.NoticeFile;

public record NoticeFileResponse(
        @JsonProperty("file_id") UUID fileId,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("file_url") String fileUrl
) {
    public static NoticeFileResponse from(NoticeFile file) {
        return new NoticeFileResponse(file.getId(), file.getFileName(), file.getFileUrl());
    }
}
