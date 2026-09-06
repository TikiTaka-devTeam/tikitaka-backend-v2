package com.tikitaka.assignment.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.assignment.dto.request.AssignmentCreateRequest;
import com.tikitaka.assignment.dto.request.AssignmentUpdateRequest;
import com.tikitaka.assignment.dto.response.AssignmentCloseResponse;
import com.tikitaka.assignment.dto.response.AssignmentCreateResponse;
import com.tikitaka.assignment.dto.response.AssignmentDeleteResponse;
import com.tikitaka.assignment.dto.response.AssignmentDetailResponse;
import com.tikitaka.assignment.dto.response.AssignmentListResponse;
import com.tikitaka.assignment.dto.response.AssignmentSummaryResponse;
import com.tikitaka.assignment.dto.response.AssignmentUpdateResponse;
import com.tikitaka.assignment.service.AssignmentService;
import com.tikitaka.global.config.OpenApiConfig;
import com.tikitaka.global.security.CurrentUserResolver;
import com.tikitaka.user.entity.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(
        name = "Assignment",
        description = "Assignment 생성/조회/수정/삭제 API"
)
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final CurrentUserResolver currentUserResolver;

    @Operation(
            summary = "ASG-001 과제 목록 조회"
    )
    @GetMapping(
            "/api/v1/spaces/{spaceId}/assignments"
    )
    public AssignmentListResponse getAssignments(
            @PathVariable UUID spaceId,
            Authentication authentication
    ) {
        User currentUser =
                currentUserResolver.resolve(
                        authentication
                );

        return assignmentService.getAssignments(
                spaceId,
                currentUser
        );
    }

    @Operation(
            summary = "ASG-002 과제 현황 요약 조회"
    )
    @GetMapping(
            "/api/v1/spaces/{spaceId}/assignments/summary"
    )
    public AssignmentSummaryResponse getAssignmentSummary(
            @PathVariable UUID spaceId,
            Authentication authentication
    ) {
        User currentUser =
                currentUserResolver.resolve(
                        authentication
                );

        return assignmentService
                .getAssignmentSummary(
                        spaceId,
                        currentUser
                );
    }

    @Operation(
            summary = "ASG-003 과제 상세 조회"
    )
    @GetMapping(
            "/api/v1/assignments/{assignmentId}"
    )
    public AssignmentDetailResponse getAssignment(
            @PathVariable UUID assignmentId,
            Authentication authentication
    ) {
        User currentUser =
                currentUserResolver.resolve(
                        authentication
                );

        return assignmentService.getAssignment(
                assignmentId,
                currentUser
        );
    }

    @Operation(
            summary = "ASG-004 과제 등록",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @Content(
                                            mediaType =
                                                    MediaType.MULTIPART_FORM_DATA_VALUE,
                                            encoding = {
                                                    @Encoding(
                                                            name = "assignment_data",
                                                            contentType =
                                                                    MediaType.APPLICATION_JSON_VALUE
                                                    )
                                            }
                                    )
                    )
    )
    @PostMapping(
            value =
                    "/api/v1/spaces/{spaceId}/assignments",
            consumes =
                    MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public AssignmentCreateResponse createAssignment(
            @PathVariable UUID spaceId,

            @Valid
            @RequestPart("assignment_data")
            AssignmentCreateRequest assignmentData,

            @Parameter(
                    description = "과제 첨부파일",
                    content =
                            @Content(
                                    mediaType =
                                            MediaType.APPLICATION_OCTET_STREAM_VALUE,
                                    schema =
                                            @Schema(
                                                    type = "string",
                                                    format = "binary"
                                            )
                            )
            )
            @RequestPart(
                    value = "files",
                    required = false
            )
            List<MultipartFile> files,

            Authentication authentication
    ) {
        User currentUser =
                currentUserResolver.resolve(
                        authentication
                );

        return assignmentService.createAssignment(
                spaceId,
                assignmentData,
                files,
                currentUser
        );
    }

    @Operation(
            summary = "ASG-005 과제 수정",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @Content(
                                            mediaType =
                                                    MediaType.MULTIPART_FORM_DATA_VALUE,
                                            encoding = {
                                                    @Encoding(
                                                            name = "assignment_data",
                                                            contentType =
                                                                    MediaType.APPLICATION_JSON_VALUE
                                                    )
                                            }
                                    )
                    )
    )
    @PatchMapping(
            value =
                    "/api/v1/assignments/{assignmentId}",
            consumes =
                    MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public AssignmentUpdateResponse updateAssignment(
            @PathVariable UUID assignmentId,

            @Valid
            @RequestPart("assignment_data")
            AssignmentUpdateRequest assignmentData,

            @Parameter(
                    description =
                            "새로 추가할 과제 첨부파일",
                    content =
                            @Content(
                                    mediaType =
                                            MediaType.APPLICATION_OCTET_STREAM_VALUE,
                                    schema =
                                            @Schema(
                                                    type = "string",
                                                    format = "binary"
                                            )
                            )
            )
            @RequestPart(
                    value = "new_files",
                    required = false
            )
            List<MultipartFile> newFiles,

            Authentication authentication
    ) {
        User currentUser =
                currentUserResolver.resolve(
                        authentication
                );

        return assignmentService.updateAssignment(
                assignmentId,
                assignmentData,
                newFiles,
                currentUser
        );
    }

    @Operation(
            summary = "ASG-006 과제 수동 마감"
    )
    @PatchMapping(
            "/api/v1/assignments/{assignmentId}/close"
    )
    public AssignmentCloseResponse closeAssignment(
            @PathVariable UUID assignmentId,
            Authentication authentication
    ) {
        User currentUser =
                currentUserResolver.resolve(
                        authentication
                );

        return assignmentService.closeAssignment(
                assignmentId,
                currentUser
        );
    }

    @Operation(
            summary = "ASG-007 과제 삭제"
    )
    @DeleteMapping(
            "/api/v1/assignments/{assignmentId}"
    )
    public AssignmentDeleteResponse deleteAssignment(
            @PathVariable UUID assignmentId,
            Authentication authentication
    ) {
        User currentUser =
                currentUserResolver.resolve(
                        authentication
                );

        return assignmentService.deleteAssignment(
                assignmentId,
                currentUser
        );
    }
}