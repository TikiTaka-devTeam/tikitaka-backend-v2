package com.tikitaka.assignment.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.assignment.dto.request.AssignmentCreateRequest;
import com.tikitaka.assignment.dto.response.AssignmentCreateResponse;
import com.tikitaka.assignment.dto.response.AssignmentDetailResponse;
import com.tikitaka.assignment.dto.response.AssignmentFileResponse;
import com.tikitaka.assignment.dto.response.AssignmentListItemResponse;
import com.tikitaka.assignment.dto.response.AssignmentListResponse;
import com.tikitaka.assignment.dto.response.AssignmentSubmissionResponse;
import com.tikitaka.assignment.dto.response.AssignmentSummaryResponse;
import com.tikitaka.assignment.entity.Assignment;
import com.tikitaka.assignment.entity.AssignmentFile;
import com.tikitaka.assignment.entity.AssignmentGrade;
import com.tikitaka.assignment.entity.AssignmentSubmission;
import com.tikitaka.assignment.entity.GradingStatus;
import com.tikitaka.assignment.repository.AssignmentFileRepository;
import com.tikitaka.assignment.repository.AssignmentGradeRepository;
import com.tikitaka.assignment.repository.AssignmentRepository;
import com.tikitaka.assignment.repository.AssignmentSubmissionRepository;
import com.tikitaka.assignment.repository.SubmissionFileRepository;
import com.tikitaka.assignment.exception.AssignmentErrorCode;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.s3.FileUploadType;
import com.tikitaka.global.s3.S3Service;
import com.tikitaka.global.s3.S3UploadResult;
import com.tikitaka.space.entity.PermissionType;
import com.tikitaka.space.entity.Space;
import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberRole;
import com.tikitaka.space.entity.SpaceMemberStatus;
import com.tikitaka.space.repository.SpaceMemberPermissionRepository;
import com.tikitaka.space.repository.SpaceMemberRepository;
import com.tikitaka.space.repository.SpaceRepository;
import com.tikitaka.user.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssignmentService {

    private static final int PREVIEW_LENGTH = 30;

    private final AssignmentRepository assignmentRepository;
    private final AssignmentFileRepository assignmentFileRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final SubmissionFileRepository submissionFileRepository;
    private final AssignmentGradeRepository assignmentGradeRepository;
    private final SpaceRepository spaceRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final SpaceMemberPermissionRepository spaceMemberPermissionRepository;
    private final S3Service s3Service;

    // ASG-001
    public AssignmentListResponse getAssignments(UUID spaceId, User currentUser) {
        SpaceMember member = getApprovedMember(spaceId, currentUser.getId());

        List<AssignmentListItemResponse> responses = assignmentRepository
                .findAllBySpaceIdAndDeletedFalseOrderByCreatedAtDesc(spaceId)
                .stream()
                .map(assignment -> toListItem(assignment, member))
                .toList();

        return new AssignmentListResponse(responses);
    }

    // ASG-002
    public AssignmentSummaryResponse getAssignmentSummary(UUID spaceId, User currentUser) {
        SpaceMember member = getApprovedMember(spaceId, currentUser.getId());
        List<Assignment> assignments = assignmentRepository
                .findAllBySpaceIdAndDeletedFalseOrderByCreatedAtDesc(spaceId);

        if (member.getRole() == SpaceMemberRole.STUDENT) {
            long notSubmittedCount = assignments.stream()
                    .filter(assignment -> !isClosed(assignment))
                    .filter(assignment -> !assignmentSubmissionRepository
                            .existsByAssignmentIdAndStudentId(assignment.getId(), currentUser.getId()))
                    .count();

            return AssignmentSummaryResponse.forStudent(notSubmittedCount);
        }

        long beforeDeadlineCount = assignments.stream()
                .filter(assignment -> !isClosed(assignment))
                .count();

        long gradingPendingCount = assignments.stream()
                .filter(this::isClosed)
                .filter(assignment -> assignment.getGradingStatus() == GradingStatus.DRAFT)
                .count();

        return AssignmentSummaryResponse.forManager(beforeDeadlineCount, gradingPendingCount);
    }

    // ASG-003
    @Transactional
    public AssignmentDetailResponse getAssignment(UUID assignmentId, User currentUser) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new BusinessException(AssignmentErrorCode.ASSIGNMENT_NOT_FOUND));

        SpaceMember member = getApprovedMember(assignment.getSpace().getId(), currentUser.getId());
        assignment.increaseViewCount();

        List<AssignmentFileResponse> files = assignmentFileRepository
                .findAllByAssignmentId(assignmentId)
                .stream()
                .map(AssignmentFileResponse::from)
                .toList();

        AssignmentSubmissionResponse mySubmission = null;
        BigDecimal score = null;

        if (member.getRole() == SpaceMemberRole.STUDENT) {
            mySubmission = assignmentSubmissionRepository
                    .findByAssignmentIdAndStudentId(assignmentId, currentUser.getId())
                    .map(this::toSubmissionResponse)
                    .orElse(null);

            if (assignment.getGradingStatus() == GradingStatus.FINALIZED) {
                score = assignmentGradeRepository
                        .findByAssignmentIdAndStudentId(assignmentId, currentUser.getId())
                        .map(AssignmentGrade::getScore)
                        .orElse(null);
            }
        }

        return new AssignmentDetailResponse(
                assignment.getId(),
                assignment.getTitle(),
                assignment.getDescription(),
                assignment.getCreatedAt(),
                assignment.getSpace().getProfessor().getName(),
                assignment.getViewCount(),
                files,
                assignment.getDueAt(),
                statusOf(assignment),
                mySubmission,
                assignment.getGradingStatus().name(),
                score,
                assignment.getMaxScore()
        );
    }

    // ASG-004
    @Transactional
    public AssignmentCreateResponse createAssignment(
            UUID spaceId,
            AssignmentCreateRequest request,
            List<MultipartFile> files,
            User currentUser
    ) {
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new BusinessException(AssignmentErrorCode.SPACE_NOT_FOUND));

        SpaceMember member = getApprovedMember(spaceId, currentUser.getId());
        validateAssignmentManager(member);

        Assignment assignment = Assignment.create(
                space,
                currentUser,
                request.title(),
                request.description(),
                request.dueAt(),
                request.autoClose()
        );
        assignmentRepository.save(assignment);

        List<AssignmentFileResponse> fileResponses = uploadAssignmentFiles(assignment, files);

        return new AssignmentCreateResponse(
                assignment.getId(),
                assignment.getTitle(),
                assignment.getDescription(),
                assignment.getDueAt(),
                request.closeType().name(),
                statusOf(assignment),
                fileResponses
        );
    }

    private AssignmentListItemResponse toListItem(Assignment assignment, SpaceMember member) {
        String submissionStatus = null;

        if (member.getRole() == SpaceMemberRole.STUDENT) {
            submissionStatus = assignmentSubmissionRepository
                    .findByAssignmentIdAndStudentId(assignment.getId(), member.getUser().getId())
                    .map(submission -> submission.getStatus().name())
                    .orElse("NOT_SUBMITTED");
        }

        return new AssignmentListItemResponse(
                assignment.getId(),
                assignment.getTitle(),
                contentPreview(assignment.getDescription()),
                assignment.getDueAt(),
                statusOf(assignment),
                submissionStatus
        );
    }

    private AssignmentSubmissionResponse toSubmissionResponse(AssignmentSubmission submission) {
        List<AssignmentFileResponse> files = submissionFileRepository
                .findAllBySubmissionId(submission.getId())
                .stream()
                .map(AssignmentFileResponse::from)
                .toList();

        return new AssignmentSubmissionResponse(
                submission.getId(),
                submission.getComment(),
                files,
                submission.getStatus().name(),
                submission.getSubmittedAt()
        );
    }

    private List<AssignmentFileResponse> uploadAssignmentFiles(
            Assignment assignment,
            List<MultipartFile> files
    ) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        List<S3UploadResult> uploaded = s3Service.uploadAll(
                files,
                "assignments/" + assignment.getId(),
                FileUploadType.ASSIGNMENT_ATTACHMENT
        );

        List<AssignmentFile> entities = new ArrayList<>();
        for (int i = 0; i < uploaded.size(); i++) {
            MultipartFile multipartFile = files.get(i);
            S3UploadResult result = uploaded.get(i);

            entities.add(AssignmentFile.create(
                    assignment,
                    multipartFile.getOriginalFilename(),
                    result.url()
            ));
        }

        return assignmentFileRepository.saveAll(entities)
                .stream()
                .map(AssignmentFileResponse::from)
                .toList();
    }

    private SpaceMember getApprovedMember(UUID spaceId, UUID userId) {
        return spaceMemberRepository
                .findBySpaceIdAndUserIdAndStatus(spaceId, userId, SpaceMemberStatus.APPROVED)
                .orElseThrow(() -> new BusinessException(AssignmentErrorCode.SPACE_MEMBER_REQUIRED));
    }

    private void validateAssignmentManager(SpaceMember member) {
        if (member.getRole() == SpaceMemberRole.PROFESSOR) {
            return;
        }

        if (member.getRole() == SpaceMemberRole.ASSISTANT
                && spaceMemberPermissionRepository.existsBySpaceMemberIdAndPermission(
                        member.getId(),
                        PermissionType.ASSIGNMENT_MANAGE
                )) {
            return;
        }

        throw new BusinessException(AssignmentErrorCode.ASSIGNMENT_MANAGE_FORBIDDEN);
    }

    private boolean isClosed(Assignment assignment) {
        if (assignment.isClosed()) {
            return true;
        }

        return assignment.isAutoClose()
                && !Instant.now().isBefore(assignment.getDueAt());
    }

    private String statusOf(Assignment assignment) {
        return isClosed(assignment) ? "CLOSED" : "OPEN";
    }

    private String contentPreview(String description) {
        if (description == null) {
            return "";
        }

        String normalized = description.strip().replaceAll("\\s+", " ");
        if (normalized.length() <= PREVIEW_LENGTH) {
            return normalized;
        }

        return normalized.substring(0, PREVIEW_LENGTH) + "...";
    }
}
