package com.tikitaka.note.controller;

import java.util.UUID;
import java.util.List;
import com.tikitaka.note.dto.request.FixerRequest;
import com.tikitaka.global.config.OpenApiConfig;
import com.tikitaka.global.security.CurrentUserResolver;
import com.tikitaka.note.dto.request.StrokeSyncRequest;
import com.tikitaka.note.dto.response.*;
import com.tikitaka.note.service.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Note", description = "학생 개인 필기·교수 공유 필기·수정 메모 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class NoteController {
    private final NoteService service;
    private final CurrentUserResolver users;

    @GetMapping("/api/v1/slides/{slideId}/private-strokes")
    @Operation(summary = "NTE-001 학생 개인 필기 조회")
    public StrokeLayerResponse getPrivate(@PathVariable UUID slideId, Authentication authentication) {
        return service.getPrivate(slideId, users.resolve(authentication));
    }

    @PostMapping("/api/v1/slides/{slideId}/private-strokes/sync")
    @Operation(summary = "NTE-002 학생 개인 필기 작업 일괄 저장")
    public StrokeSyncResponse syncPrivate(@PathVariable UUID slideId,
            @Valid @RequestBody StrokeSyncRequest request, Authentication authentication) {
        return service.syncPrivate(slideId, request, users.resolve(authentication));
    }

    @GetMapping("/api/v1/slides/{slideId}/shared-strokes")
    @Operation(summary = "NTE-003 교수 공유 필기 조회")
    public StrokeLayerResponse getShared(@PathVariable UUID slideId, Authentication authentication) {
        return service.getShared(slideId, users.resolve(authentication));
    }

    @PostMapping("/api/v1/slides/{slideId}/shared-strokes/sync")
    @Operation(summary = "NTE-004 교수 공유 필기 작업 일괄 저장")
    public StrokeSyncResponse syncShared(@PathVariable UUID slideId,
            @Valid @RequestBody StrokeSyncRequest request, Authentication authentication) {
        return service.syncShared(slideId, request, users.resolve(authentication));
    }

    @PostMapping("/api/v1/slides/{slideId}/fixers")
    @Operation(summary = "NTE-005 교수 개인 수정 메모 작성")
    public FixerResponse.Created createFixer(@PathVariable UUID slideId,
            @Valid @RequestBody FixerRequest request, Authentication authentication) {
        return service.createFixer(slideId, request, users.resolve(authentication));
    }

    @GetMapping("/api/v1/slides/{slideId}/fixers")
    @Operation(summary = "NTE-006 교수 개인 수정 메모 조회")
    public List<FixerResponse> getFixers(@PathVariable UUID slideId, Authentication authentication) {
        return service.getFixers(slideId, users.resolve(authentication));
    }

    @PatchMapping("/api/v1/fixers/{fixerId}/check")
    @Operation(summary = "NTE-007 수정 메모 완료 처리")
    public FixerResponse.Checked checkFixer(@PathVariable UUID fixerId, Authentication authentication) {
        return service.checkFixer(fixerId, users.resolve(authentication));
    }
}
