package com.tikitaka.space.controller;

import com.tikitaka.global.config.OpenApiConfig;
import com.tikitaka.global.security.CurrentUserResolver;
import com.tikitaka.space.dto.request.JoinRequestActionRequest;
import com.tikitaka.space.dto.request.JoinSettingsRequest;
import com.tikitaka.space.dto.response.JoinRequestActionResponse;
import com.tikitaka.space.dto.response.JoinRequestListResponse;
import com.tikitaka.space.dto.response.JoinSettingsResponse;
import com.tikitaka.space.dto.response.MemberDetailResponse;
import com.tikitaka.space.dto.response.MemberListResponse;
import com.tikitaka.space.service.SpaceMemberService;
import com.tikitaka.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Space Member", description = "Space 멤버/가입 요청 관리 API")
@RestController
@RequestMapping("/api/v1/spaces/{spaceId}")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class SpaceMemberController {

    private final SpaceMemberService spaceMemberService;
    private final CurrentUserResolver currentUserResolver;

    @Operation(summary = "MBR-001 멤버 목록 조회")
    @GetMapping("/members")
    public ResponseEntity<MemberListResponse> getMembers(
            @PathVariable UUID spaceId,
            Authentication authentication
    ) {
        User currentUser = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(spaceMemberService.getMembers(spaceId, currentUser));
    }

    @Operation(summary = "MBR-002 멤버 상세 조회")
    @GetMapping("/members/{memberId}")
    public ResponseEntity<MemberDetailResponse> getMemberDetail(
            @PathVariable UUID spaceId,
            @PathVariable UUID memberId,
            Authentication authentication
    ) {
        User currentUser = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(
                spaceMemberService.getMemberDetail(spaceId, memberId, currentUser)
        );
    }

    @Operation(summary = "MBR-003 가입 요청 목록 조회")
    @GetMapping("/join-requests")
    public ResponseEntity<JoinRequestListResponse> getJoinRequests(
            @PathVariable UUID spaceId,
            Authentication authentication
    ) {
        User currentUser = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(
                spaceMemberService.getJoinRequests(spaceId, currentUser)
        );
    }

    @Operation(summary = "MBR-004 가입 요청 승인")
    @PatchMapping("/join-requests/approve")
    public ResponseEntity<JoinRequestActionResponse> approveJoinRequests(
            @PathVariable UUID spaceId,
            @Valid @RequestBody JoinRequestActionRequest request,
            Authentication authentication
    ) {
        User currentUser = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(
                spaceMemberService.approveJoinRequests(spaceId, request, currentUser)
        );
    }

    @Operation(summary = "MBR-005 가입 요청 거절")
    @PatchMapping("/join-requests/deny")
    public ResponseEntity<JoinRequestActionResponse> denyJoinRequests(
            @PathVariable UUID spaceId,
            @Valid @RequestBody JoinRequestActionRequest request,
            Authentication authentication
    ) {
        User currentUser = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(
                spaceMemberService.denyJoinRequests(spaceId, request, currentUser)
        );
    }

    @Operation(summary = "MBR-007 자동 승인 설정")
    @PatchMapping("/join-settings")
    public ResponseEntity<JoinSettingsResponse> updateJoinSettings(
            @PathVariable UUID spaceId,
            @Valid @RequestBody JoinSettingsRequest request,
            Authentication authentication
    ) {
        User currentUser = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(
                spaceMemberService.updateJoinSettings(spaceId, request, currentUser)
        );
    }

    @Operation(summary = "MBR-008 멤버 내보내기")
    @DeleteMapping("/members/{memberId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID spaceId,
            @PathVariable UUID memberId,
            Authentication authentication
    ) {
        User currentUser = currentUserResolver.resolve(authentication);
        spaceMemberService.removeMember(spaceId, memberId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
