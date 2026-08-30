package com.tikitaka.notice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.global.config.OpenApiConfig;
import com.tikitaka.global.security.CurrentUserResolver;
import com.tikitaka.notice.dto.request.NoticeCreateRequest;
import com.tikitaka.notice.dto.request.NoticeUpdateRequest;
import com.tikitaka.notice.dto.response.NoticeCreateResponse;
import com.tikitaka.notice.dto.response.NoticeDeleteResponse;
import com.tikitaka.notice.dto.response.NoticeDetailResponse;
import com.tikitaka.notice.dto.response.NoticeListResponse;
import com.tikitaka.notice.dto.response.NoticeUpdateResponse;
import com.tikitaka.notice.service.NoticeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(
        name = "Notice",
        description = "공지사항 생성/조회/수정/삭제 API"
)
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class NoticeController {

    private final NoticeService noticeService;
    private final CurrentUserResolver currentUserResolver;

    @Operation(summary = "NOT-001 공지사항 목록 조회")
    @GetMapping("/api/v1/spaces/{spaceId}/notices")
    public NoticeListResponse getNotices(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return noticeService.getNotices(
                spaceId,
                cursor,
                size,
                currentUserResolver.resolve(authentication)
        );
    }

    @Operation(summary = "NOT-002 공지 상세 조회 및 읽음 처리")
    @GetMapping("/api/v1/notices/{noticeId}")
    public NoticeDetailResponse getNotice(
            @PathVariable UUID noticeId,
            Authentication authentication
    ) {
        return noticeService.getNotice(
                noticeId,
                currentUserResolver.resolve(authentication)
        );
    }

    @Operation(summary = "NOT-003 공지 등록")
    @RequestBody(
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    encoding = {
                            @Encoding(
                                    name = "notice_data",
                                    contentType = MediaType.APPLICATION_JSON_VALUE
                            ),
                            @Encoding(
                                    name = "files",
                                    contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE
                            )
                    }
            )
    )
    @PostMapping(
            value = "/api/v1/spaces/{spaceId}/notices",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public NoticeCreateResponse createNotice(
            @PathVariable UUID spaceId,
            @Valid @RequestPart("notice_data") NoticeCreateRequest noticeData,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            Authentication authentication
    ) {
        return noticeService.createNotice(
                spaceId,
                noticeData,
                files,
                currentUserResolver.resolve(authentication)
        );
    }

    @Operation(summary = "NOT-004 공지 수정")
    @RequestBody(
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    encoding = {
                            @Encoding(
                                    name = "notice_data",
                                    contentType = MediaType.APPLICATION_JSON_VALUE
                            ),
                            @Encoding(
                                    name = "new_files",
                                    contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE
                            )
                    }
            )
    )
    @PatchMapping(
            value = "/api/v1/notices/{noticeId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public NoticeUpdateResponse updateNotice(
            @PathVariable UUID noticeId,
            @Valid @RequestPart("notice_data") NoticeUpdateRequest noticeData,
            @RequestPart(value = "new_files", required = false) List<MultipartFile> newFiles,
            Authentication authentication
    ) {
        return noticeService.updateNotice(
                noticeId,
                noticeData,
                newFiles,
                currentUserResolver.resolve(authentication)
        );
    }

    @Operation(summary = "NOT-005 공지 삭제")
    @DeleteMapping("/api/v1/notices/{noticeId}")
    public NoticeDeleteResponse deleteNotice(
            @PathVariable UUID noticeId,
            Authentication authentication
    ) {
        noticeService.deleteNotice(
                noticeId,
                currentUserResolver.resolve(authentication)
        );

        return new NoticeDeleteResponse("공지가 삭제되었습니다.");
    }
}