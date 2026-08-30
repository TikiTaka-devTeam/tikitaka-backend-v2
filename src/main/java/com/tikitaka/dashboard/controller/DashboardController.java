package com.tikitaka.dashboard.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tikitaka.dashboard.dto.DashboardAssignmentsResponse;
import com.tikitaka.dashboard.dto.DashboardTimetableItem;
import com.tikitaka.dashboard.service.DashboardService;
import com.tikitaka.global.config.OpenApiConfig;
import com.tikitaka.global.security.CurrentUserResolver;
import com.tikitaka.user.entity.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

@Tag(name = "Dashboard", description = "대시보드 API")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUserResolver currentUserResolver;

    @Operation(
            summary = "DSH-001 학기별 시간표 조회",
            description = "승인된 활성 Space의 시간표를 학년도와 학기별로 조회합니다."
    )
    @GetMapping("/timetable")
    public ResponseEntity<List<DashboardTimetableItem>> getTimetable(
            Authentication authentication,
            @RequestParam @Min(1) int year,
            @RequestParam @Pattern(regexp = "[12]") String semester
    ) {
        User currentUser = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(
                dashboardService.getTimetable(currentUser.getId(), year, semester)
        );
    }

    @Operation(
            summary = "DSH-002 과제 목록 조회",
            description = "승인된 활성 Space의 마감되지 않은 과제를 역할과 제출 여부에 관계없이 마감 임박 순으로 조회합니다."
    )
    @GetMapping("/assignments")
    public ResponseEntity<DashboardAssignmentsResponse> getAssignments(
            Authentication authentication
    ) {
        User currentUser = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(
                dashboardService.getAssignments(currentUser.getId())
        );
    }
}
