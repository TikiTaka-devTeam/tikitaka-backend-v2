package com.tikitaka.assignment.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.assignment.dto.request.AssignmentCreateRequest;
import com.tikitaka.assignment.dto.request.AssignmentUpdateRequest;
import com.tikitaka.assignment.dto.response.AssignmentCloseResponse;
import com.tikitaka.assignment.dto.response.AssignmentCreateResponse;
import com.tikitaka.assignment.dto.response.AssignmentDeleteResponse;
import com.tikitaka.assignment.dto.response.AssignmentDetailResponse;
import com.tikitaka.assignment.dto.response.AssignmentFileResponse;
import com.tikitaka.assignment.dto.response.AssignmentListItemResponse;
import com.tikitaka.assignment.dto.response.AssignmentListResponse;
import com.tikitaka.assignment.dto.response.AssignmentSubmissionResponse;
import com.tikitaka.assignment.dto.response.AssignmentSubmitResponse;
import com.tikitaka.assignment.dto.response.AssignmentSummaryResponse;
import com.tikitaka.assignment.dto.response.AssignmentUpdateResponse;
import com.tikitaka.assignment.entity.Assignment;
import com.tikitaka.assignment.entity.AssignmentFile;
import com.tikitaka.assignment.entity.AssignmentGrade;
import com.tikitaka.assignment.entity.AssignmentSubmission;
import com.tikitaka.assignment.entity.GradingStatus;
import com.tikitaka.assignment.entity.SubmissionFile;
import com.tikitaka.assignment.entity.SubmissionStatus;
import com.tikitaka.assignment.exception.AssignmentErrorCode;
import com.tikitaka.assignment.repository.AssignmentFileRepository;
import com.tikitaka.assignment.repository.AssignmentGradeRepository;
import com.tikitaka.assignment.repository.AssignmentRepository;
import com.tikitaka.assignment.repository.AssignmentSubmissionRepository;
import com.tikitaka.assignment.repository.SubmissionFileRepository;
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
    public AssignmentListResponse getAssignments(
            UUID spaceId,
            User currentUser
    ) {
        SpaceMember member =
                getApprovedMember(
                        spaceId,
                        currentUser.getId()
                );

        List<AssignmentListItemResponse> responses =
                assignmentRepository
                        .findAllBySpaceIdAndDeletedFalseOrderByCreatedAtDesc(
                                spaceId
                        )
                        .stream()
                        .map(assignment ->
                                toListItem(
                                        assignment,
                                        member
                                )
                        )
                        .toList();

        return new AssignmentListResponse(
                responses
        );
    }

    // ASG-002
    public AssignmentSummaryResponse getAssignmentSummary(
            UUID spaceId,
            User currentUser
    ) {
        SpaceMember member =
                getApprovedMember(
                        spaceId,
                        currentUser.getId()
                );

        List<Assignment> assignments =
                assignmentRepository
                        .findAllBySpaceIdAndDeletedFalseOrderByCreatedAtDesc(
                                spaceId
                        );

        if (member.getRole() == SpaceMemberRole.STUDENT) {

            long notSubmittedCount =
                    assignments.stream()
                            .filter(assignment ->
                                    !isClosed(assignment)
                            )
                            .filter(assignment ->
                                    !assignmentSubmissionRepository
                                            .existsByAssignmentIdAndStudentId(
                                                    assignment.getId(),
                                                    currentUser.getId()
                                            )
                            )
                            .count();

            return AssignmentSummaryResponse.forStudent(
                    notSubmittedCount
            );
        }

        long beforeDeadlineCount =
                assignments.stream()
                        .filter(assignment ->
                                !isClosed(assignment)
                        )
                        .count();

        long gradingPendingCount =
                assignments.stream()
                        .filter(this::isClosed)
                        .filter(assignment ->
                                assignment.getGradingStatus()
                                        == GradingStatus.DRAFT
                        )
                        .count();

        return AssignmentSummaryResponse.forManager(
                beforeDeadlineCount,
                gradingPendingCount
        );
    }

    // ASG-003
    @Transactional
    public AssignmentDetailResponse getAssignment(
            UUID assignmentId,
            User currentUser
    ) {
        Assignment assignment =
                getActiveAssignment(
                        assignmentId
                );

        SpaceMember member =
                getApprovedMember(
                        assignment.getSpace().getId(),
                        currentUser.getId()
                );

        assignment.increaseViewCount();

        List<AssignmentFileResponse> files =
                assignmentFileRepository
                        .findAllByAssignmentId(
                                assignmentId
                        )
                        .stream()
                        .map(AssignmentFileResponse::from)
                        .toList();

        AssignmentSubmissionResponse mySubmission = null;
        BigDecimal score = null;

        if (member.getRole() == SpaceMemberRole.STUDENT) {

            mySubmission =
                    assignmentSubmissionRepository
                            .findByAssignmentIdAndStudentId(
                                    assignmentId,
                                    currentUser.getId()
                            )
                            .map(this::toSubmissionResponse)
                            .orElse(null);

            if (assignment.getGradingStatus()
                    == GradingStatus.FINALIZED) {

                score =
                        assignmentGradeRepository
                                .findByAssignmentIdAndStudentId(
                                        assignmentId,
                                        currentUser.getId()
                                )
                                .map(AssignmentGrade::getScore)
                                .orElse(null);
            }
        }

        return new AssignmentDetailResponse(
                assignment.getId(),
                assignment.getTitle(),
                assignment.getDescription(),
                assignment.getCreatedAt(),
                assignment.getSpace()
                        .getProfessor()
                        .getName(),
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
        Space space =
                spaceRepository
                        .findById(spaceId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        AssignmentErrorCode.SPACE_NOT_FOUND
                                )
                        );

        SpaceMember member =
                getApprovedMember(
                        spaceId,
                        currentUser.getId()
                );

        validateAssignmentManager(
                member
        );

        Assignment assignment =
                Assignment.create(
                        space,
                        currentUser,
                        request.title(),
                        request.description(),
                        request.dueAt(),
                        request.autoClose()
                );

        assignmentRepository.save(
                assignment
        );

        List<AssignmentFileResponse> fileResponses =
                uploadAssignmentFiles(
                        assignment,
                        files
                );

        return new AssignmentCreateResponse(
                assignment.getId(),
                assignment.getTitle(),
                assignment.getDescription(),
                assignment.getDueAt(),
                closeTypeOf(assignment),
                statusOf(assignment),
                fileResponses
        );
    }

    // ASG-005
    @Transactional
    public AssignmentUpdateResponse updateAssignment(
            UUID assignmentId,
            AssignmentUpdateRequest request,
            List<MultipartFile> newFiles,
            User currentUser
    ) {
        Assignment assignment =
                getActiveAssignment(
                        assignmentId
                );

        SpaceMember member =
                getApprovedMember(
                        assignment.getSpace().getId(),
                        currentUser.getId()
                );

        validateAssignmentManager(
                member
        );

        assignment.update(
                request.title(),
                request.description(),
                request.dueAt(),
                request.autoClose()
        );

        updateRetainedFiles(
                assignment,
                request.retainedFileIds()
        );

        uploadAssignmentFiles(
                assignment,
                newFiles
        );

        List<AssignmentFileResponse> files =
                assignmentFileRepository
                        .findAllByAssignmentId(
                                assignmentId
                        )
                        .stream()
                        .map(AssignmentFileResponse::from)
                        .toList();

        return new AssignmentUpdateResponse(
                assignment.getId(),
                assignment.getTitle(),
                assignment.getDescription(),
                assignment.getDueAt(),
                closeTypeOf(assignment),
                statusOf(assignment),
                files
        );
    }

    // ASG-006
    @Transactional
    public AssignmentCloseResponse closeAssignment(
            UUID assignmentId,
            User currentUser
    ) {
        Assignment assignment =
                getActiveAssignment(
                        assignmentId
                );

        SpaceMember member =
                getApprovedMember(
                        assignment.getSpace().getId(),
                        currentUser.getId()
                );

        validateAssignmentManager(
                member
        );

        if (!assignment.isClosed()) {
            assignment.close();
        }

        return new AssignmentCloseResponse(
                assignment.getId(),
                "CLOSED"
        );
    }

    // ASG-007
    @Transactional
    public AssignmentDeleteResponse deleteAssignment(
            UUID assignmentId,
            User currentUser
    ) {
        Assignment assignment =
                getActiveAssignment(
                        assignmentId
                );

        SpaceMember member =
                getApprovedMember(
                        assignment.getSpace().getId(),
                        currentUser.getId()
                );

        validateAssignmentManager(
                member
        );

        assignment.delete();

        return new AssignmentDeleteResponse(
                assignment.getId(),
                true
        );
    }

    // ASG-008
    @Transactional
    public AssignmentSubmitResponse submitAssignment(
            UUID assignmentId,
            String comment,
            List<MultipartFile> files,
            User currentUser
    ) {
        Assignment assignment =
                getActiveAssignment(
                        assignmentId
                );

        SpaceMember member =
                getApprovedMember(
                        assignment.getSpace().getId(),
                        currentUser.getId()
                );

        validateStudent(
                member
        );

        validateAssignmentOpen(
                assignment
        );

        if (assignmentSubmissionRepository
                .existsByAssignmentIdAndStudentId(
                        assignmentId,
                        currentUser.getId()
                )) {

            throw new BusinessException(
                    AssignmentErrorCode
                            .SUBMISSION_ALREADY_EXISTS
            );
        }

        AssignmentSubmission submission =
                AssignmentSubmission.create(
                        assignment,
                        currentUser,
                        comment,
                        SubmissionStatus.SUBMITTED
                );

        assignmentSubmissionRepository.save(
                submission
        );

        List<AssignmentFileResponse> fileResponses =
                uploadSubmissionFiles(
                        submission,
                        files
                );

        return new AssignmentSubmitResponse(
                submission.getId(),
                assignment.getId(),
                submission.getComment(),
                fileResponses,
                submission.getVersion(),
                submission.getStatus().name(),
                submission.getSubmittedAt()
        );
    }

    // ASG-009
    @Transactional
    public AssignmentSubmitResponse updateMySubmission(
            UUID assignmentId,
            String comment,
            List<MultipartFile> files,
            User currentUser
    ) {
        Assignment assignment =
                getActiveAssignment(
                        assignmentId
                );

        SpaceMember member =
                getApprovedMember(
                        assignment.getSpace().getId(),
                        currentUser.getId()
                );

        validateStudent(
                member
        );

        validateAssignmentOpen(
                assignment
        );

        AssignmentSubmission submission =
                assignmentSubmissionRepository
                        .findByAssignmentIdAndStudentId(
                                assignmentId,
                                currentUser.getId()
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        AssignmentErrorCode
                                                .SUBMISSION_NOT_FOUND
                                )
                        );

        deleteSubmissionFiles(
                submission
        );

        submission.resubmit(
                comment,
                SubmissionStatus.SUBMITTED
        );

        List<AssignmentFileResponse> fileResponses =
                uploadSubmissionFiles(
                        submission,
                        files
                );

        return new AssignmentSubmitResponse(
                submission.getId(),
                assignment.getId(),
                submission.getComment(),
                fileResponses,
                submission.getVersion(),
                submission.getStatus().name(),
                submission.getSubmittedAt()
        );
    }

    private AssignmentListItemResponse toListItem(
            Assignment assignment,
            SpaceMember member
    ) {
        String submissionStatus = null;

        if (member.getRole()
                == SpaceMemberRole.STUDENT) {

            submissionStatus =
                    assignmentSubmissionRepository
                            .findByAssignmentIdAndStudentId(
                                    assignment.getId(),
                                    member.getUser().getId()
                            )
                            .map(submission ->
                                    submission
                                            .getStatus()
                                            .name()
                            )
                            .orElse(
                                    "NOT_SUBMITTED"
                            );
        }

        return new AssignmentListItemResponse(
                assignment.getId(),
                assignment.getTitle(),
                contentPreview(
                        assignment.getDescription()
                ),
                assignment.getDueAt(),
                statusOf(assignment),
                submissionStatus
        );
    }

    private AssignmentSubmissionResponse toSubmissionResponse(
            AssignmentSubmission submission
    ) {
        List<AssignmentFileResponse> files =
                submissionFileRepository
                        .findAllBySubmissionId(
                                submission.getId()
                        )
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

        List<MultipartFile> validFiles =
                files.stream()
                        .filter(file ->
                                file != null
                                        && !file.isEmpty()
                                        && file.getSize() > 0
                        )
                        .toList();

        if (validFiles.isEmpty()) {
            return List.of();
        }

        List<S3UploadResult> uploaded =
                s3Service.uploadAll(
                        validFiles,
                        "assignments/"
                                + assignment.getId(),
                        FileUploadType
                                .ASSIGNMENT_ATTACHMENT
                );

        List<AssignmentFile> entities =
                new ArrayList<>();

        for (int i = 0;
             i < uploaded.size();
             i++) {

            MultipartFile multipartFile =
                    validFiles.get(i);

            S3UploadResult result =
                    uploaded.get(i);

            entities.add(
                    AssignmentFile.create(
                            assignment,
                            multipartFile
                                    .getOriginalFilename(),
                            result.url()
                    )
            );
        }

        return assignmentFileRepository
                .saveAll(
                        entities
                )
                .stream()
                .map(AssignmentFileResponse::from)
                .toList();
    }

    private List<AssignmentFileResponse> uploadSubmissionFiles(
            AssignmentSubmission submission,
            List<MultipartFile> files
    ) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        List<MultipartFile> validFiles =
                files.stream()
                        .filter(file ->
                                file != null
                                        && !file.isEmpty()
                                        && file.getSize() > 0
                        )
                        .toList();

        if (validFiles.isEmpty()) {
            return List.of();
        }

        List<S3UploadResult> uploaded =
                s3Service.uploadAll(
                        validFiles,
                        "assignments/"
                                + submission
                                        .getAssignment()
                                        .getId()
                                + "/submissions/"
                                + submission.getId(),
                        FileUploadType
                                .ASSIGNMENT_SUBMISSION
                );

        List<SubmissionFile> entities =
                new ArrayList<>();

        for (int i = 0;
             i < uploaded.size();
             i++) {

            MultipartFile multipartFile =
                    validFiles.get(i);

            S3UploadResult result =
                    uploaded.get(i);

            entities.add(
                    SubmissionFile.create(
                            submission,
                            multipartFile
                                    .getOriginalFilename(),
                            result.url()
                    )
            );
        }

        return submissionFileRepository
                .saveAll(
                        entities
                )
                .stream()
                .map(AssignmentFileResponse::from)
                .toList();
    }

    private void deleteSubmissionFiles(
            AssignmentSubmission submission
    ) {
        List<SubmissionFile> existingFiles =
                submissionFileRepository
                        .findAllBySubmissionId(
                                submission.getId()
                        );

        for (SubmissionFile file :
                existingFiles) {

            s3Service.deleteByUrlIfManaged(
                    file.getFileUrl()
            );
        }

        submissionFileRepository
                .deleteAllBySubmissionId(
                        submission.getId()
                );

        submissionFileRepository.flush();
    }

    private void updateRetainedFiles(
            Assignment assignment,
            List<UUID> retainedFileIds
    ) {
        if (retainedFileIds == null) {
            return;
        }

        List<AssignmentFile> existingFiles =
                assignmentFileRepository
                        .findAllByAssignmentId(
                                assignment.getId()
                        );

        Set<UUID> existingFileIds =
                existingFiles.stream()
                        .map(AssignmentFile::getId)
                        .collect(
                                Collectors.toSet()
                        );

        Set<UUID> retainedIds =
                new HashSet<>(
                        retainedFileIds
                );

        boolean containsInvalidFileId =
                retainedIds.stream()
                        .anyMatch(fileId ->
                                !existingFileIds.contains(
                                        fileId
                                )
                        );

        if (containsInvalidFileId) {
            throw new BusinessException(
                    AssignmentErrorCode
                            .ASSIGNMENT_FILE_NOT_FOUND
            );
        }

        List<AssignmentFile> filesToDelete =
                existingFiles.stream()
                        .filter(file ->
                                !retainedIds.contains(
                                        file.getId()
                                )
                        )
                        .toList();

        for (AssignmentFile file :
                filesToDelete) {

            s3Service.deleteByUrlIfManaged(
                    file.getFileUrl()
            );
        }

        assignmentFileRepository.deleteAll(
                filesToDelete
        );

        assignmentFileRepository.flush();
    }

    private Assignment getActiveAssignment(
            UUID assignmentId
    ) {
        return assignmentRepository
                .findById(
                        assignmentId
                )
                .filter(assignment ->
                        !assignment.isDeleted()
                )
                .orElseThrow(() ->
                        new BusinessException(
                                AssignmentErrorCode
                                        .ASSIGNMENT_NOT_FOUND
                        )
                );
    }

    private SpaceMember getApprovedMember(
            UUID spaceId,
            UUID userId
    ) {
        return spaceMemberRepository
                .findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                        spaceId,
                        userId,
                        SpaceMemberStatus.APPROVED
                )
                .orElseThrow(() ->
                        new BusinessException(
                                AssignmentErrorCode
                                        .SPACE_MEMBER_REQUIRED
                        )
                );
    }

    private void validateAssignmentManager(
            SpaceMember member
    ) {
        if (member.getRole()
                == SpaceMemberRole.PROFESSOR) {
            return;
        }

        if (member.getRole()
                == SpaceMemberRole.ASSISTANT
                && spaceMemberPermissionRepository
                        .existsBySpaceMemberIdAndPermission(
                                member.getId(),
                                PermissionType
                                        .ASSIGNMENT_MANAGE
                        )) {
            return;
        }

        throw new BusinessException(
                AssignmentErrorCode
                        .ASSIGNMENT_MANAGE_FORBIDDEN
        );
    }

    private void validateStudent(
            SpaceMember member
    ) {
        if (member.getRole()
                == SpaceMemberRole.STUDENT) {
            return;
        }

        throw new BusinessException(
                AssignmentErrorCode.STUDENT_ONLY
        );
    }

    private void validateAssignmentOpen(
            Assignment assignment
    ) {
        if (isClosed(assignment)) {
            throw new BusinessException(
                    AssignmentErrorCode
                            .ASSIGNMENT_CLOSED
            );
        }
    }

    private boolean isClosed(
            Assignment assignment
    ) {
        if (assignment.isClosed()) {
            return true;
        }

        return assignment.isAutoClose()
                && !Instant.now()
                        .isBefore(
                                assignment.getDueAt()
                        );
    }

    private String statusOf(
            Assignment assignment
    ) {
        return isClosed(assignment)
                ? "CLOSED"
                : "OPEN";
    }

    private String closeTypeOf(
            Assignment assignment
    ) {
        return assignment.isAutoClose()
                ? "AUTO"
                : "MANUAL";
    }

    private String contentPreview(
            String description
    ) {
        if (description == null) {
            return "";
        }

        String normalized =
                description
                        .strip()
                        .replaceAll(
                                "\\s+",
                                " "
                        );

        if (normalized.length()
                <= PREVIEW_LENGTH) {
            return normalized;
        }

        return normalized.substring(
                0,
                PREVIEW_LENGTH
        ) + "...";
    }
}