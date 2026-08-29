package com.tikitaka.assignment.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.assignment.dto.request.AssignmentCreateRequest;
import com.tikitaka.assignment.dto.response.AssignmentCreateResponse;
import com.tikitaka.assignment.dto.response.AssignmentDetailResponse;
import com.tikitaka.assignment.dto.response.AssignmentListResponse;
import com.tikitaka.assignment.dto.response.AssignmentSummaryResponse;
import com.tikitaka.assignment.service.AssignmentService;
import com.tikitaka.global.security.CurrentUserResolver;
import com.tikitaka.user.entity.User;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final CurrentUserResolver currentUserResolver;

    // ASG-001
    @GetMapping("/spaces/{spaceId}/assignments")
    public ResponseEntity<AssignmentListResponse> getAssignments(
            @PathVariable UUID spaceId,
            Authentication authentication
    ) {
        User currentUser = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(assignmentService.getAssignments(spaceId, currentUser));
    }

    // ASG-002
    @GetMapping("/spaces/{spaceId}/assignments/summary")
    public ResponseEntity<AssignmentSummaryResponse> getAssignmentSummary(
            @PathVariable UUID spaceId,
            Authentication authentication
    ) {
        User currentUser = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(assignmentService.getAssignmentSummary(spaceId, currentUser));
    }

    // ASG-003
    @GetMapping("/assignments/{assignmentId}")
    public ResponseEntity<AssignmentDetailResponse> getAssignment(
            @PathVariable UUID assignmentId,
            Authentication authentication
    ) {
        User currentUser = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(assignmentService.getAssignment(assignmentId, currentUser));
    }

    // ASG-004
    @PostMapping(
            value = "/spaces/{spaceId}/assignments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<AssignmentCreateResponse> createAssignment(
            @PathVariable UUID spaceId,
            @Valid @RequestPart("assignment_data") AssignmentCreateRequest assignmentData,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            Authentication authentication
    ) {
        User currentUser = currentUserResolver.resolve(authentication);
        AssignmentCreateResponse response = assignmentService.createAssignment(
                spaceId,
                assignmentData,
                files,
                currentUser
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
