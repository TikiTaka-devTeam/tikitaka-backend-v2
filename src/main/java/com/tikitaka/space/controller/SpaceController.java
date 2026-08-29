package com.tikitaka.space.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tikitaka.global.config.OpenApiConfig;
import com.tikitaka.global.security.CurrentUserResolver;
import com.tikitaka.space.dto.request.SpaceCreateRequest;
import com.tikitaka.space.dto.request.SpaceJoinRequest;
import com.tikitaka.space.dto.request.SpaceUpdateRequest;
import com.tikitaka.space.dto.response.PendingSpaceResponse;
import com.tikitaka.space.dto.response.SpaceCreateResponse;
import com.tikitaka.space.dto.response.SpaceJoinResponse;
import com.tikitaka.space.dto.response.SpaceListResponse;
import com.tikitaka.space.dto.response.SpaceStatusResponse;
import com.tikitaka.space.dto.response.SpaceUpdateResponse;
import com.tikitaka.space.service.SpaceService;
import com.tikitaka.user.entity.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Space", description = "Space 생성/조회/참여/수정/보관 API")
@RestController
@RequestMapping("/api/v1/spaces")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class SpaceController {

    private final SpaceService spaceService;
    private final CurrentUserResolver currentUserResolver;

    @Operation(summary = "SPC-001 Space 생성", description = "교수가 Space를 생성합니다. 학년도, 학기, 색상, Space 코드는 서버에서 자동 설정합니다.")
    @PostMapping
    public ResponseEntity<SpaceCreateResponse> createSpace(
            Authentication authentication,
            @Valid @RequestBody SpaceCreateRequest request) {
        User currentUser = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(spaceService.createSpace(currentUser, request));
    }

    @Operation(summary = "SPC-002/003/009 Space 목록 조회",
            description = "기본 호출은 활성 Space, status=ARCHIVED는 보관 Space, membership_status=PENDING은 학생의 승인 대기 Space를 조회합니다.")
    @GetMapping
    public ResponseEntity<?> getSpaces(
            Authentication authentication,
            @Parameter(description = "보관 Space 조회 시 ARCHIVED", example = "ARCHIVED")
            @RequestParam(required = false) String status,
            @Parameter(description = "승인 대기 Space 조회 시 PENDING", example = "PENDING")
            @RequestParam(name = "membership_status", required = false) String membershipStatus) {
        User currentUser = currentUserResolver.resolve(authentication);

        if (membershipStatus != null) {
            if (!"PENDING".equalsIgnoreCase(membershipStatus) || status != null) {
                throw new com.tikitaka.global.exception.BusinessException(
                        com.tikitaka.space.exception.SpaceErrorCode.INVALID_SPACE_FILTER);
            }
            List<PendingSpaceResponse> response = spaceService.getPendingSpaces(currentUser);
            return ResponseEntity.ok(response);
        }

        if (status != null && !"ARCHIVED".equalsIgnoreCase(status)) {
            throw new com.tikitaka.global.exception.BusinessException(
                    com.tikitaka.space.exception.SpaceErrorCode.INVALID_SPACE_FILTER);
        }

        List<SpaceListResponse> response = spaceService.getSpaces(currentUser, status);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "SPC-004 Space 참여 신청", description = "학생이 8자리 Space 코드를 입력해 참여를 신청합니다. 자동 승인 Space는 즉시 APPROVED 처리됩니다.")
    @PostMapping("/join")
    public ResponseEntity<SpaceJoinResponse> joinSpace(
            Authentication authentication,
            @Valid @RequestBody SpaceJoinRequest request) {
        User currentUser = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(spaceService.joinSpace(currentUser, request));
    }

    @Operation(summary = "SPC-005 Space 수정", description = "Space 이름, 강의실, 수업 시간을 수정합니다. 현재 정의된 세부 권한에 SPACE 관리 권한이 없어 교수 소유자만 허용합니다.")
    @PatchMapping("/{spaceId}")
    public ResponseEntity<SpaceUpdateResponse> updateSpace(
            Authentication authentication,
            @PathVariable UUID spaceId,
            @Valid @RequestBody SpaceUpdateRequest request) {
        User currentUser = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(spaceService.updateSpace(currentUser, spaceId, request));
    }

    @Operation(summary = "SPC-006 Space 보관", description = "활성 Space를 보관 상태로 변경합니다. 교수만 가능합니다.")
    @PatchMapping("/{spaceId}/archive")
    public ResponseEntity<SpaceStatusResponse> archiveSpace(
            Authentication authentication,
            @PathVariable UUID spaceId) {
        User currentUser = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(spaceService.archiveSpace(currentUser, spaceId));
    }

    @Operation(summary = "SPC-007 Space 복원", description = "보관된 Space를 활성 상태로 복원합니다. 교수만 가능합니다.")
    @PatchMapping("/{spaceId}/restore")
    public ResponseEntity<SpaceStatusResponse> restoreSpace(
            Authentication authentication,
            @PathVariable UUID spaceId) {
        User currentUser = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(spaceService.restoreSpace(currentUser, spaceId));
    }

    @Operation(summary = "SPC-008 Space 영구 삭제", description = "Space를 영구 삭제합니다. 교수만 가능하며 204 No Content를 반환합니다.")
    @DeleteMapping("/{spaceId}")
    public ResponseEntity<Void> deleteSpace(
            Authentication authentication,
            @PathVariable UUID spaceId) {
        User currentUser = currentUserResolver.resolve(authentication);
        spaceService.deleteSpace(currentUser, spaceId);
        return ResponseEntity.noContent().build();
    }
}
