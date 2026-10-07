package com.tikitaka.assignment.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.assignment.dto.request.AssignmentCreateRequest;
import com.tikitaka.assignment.dto.request.AssignmentGradeItemRequest;
import com.tikitaka.assignment.dto.request.AssignmentGradesRequest;
import com.tikitaka.assignment.dto.request.AssignmentGradeUpdateRequest;
import com.tikitaka.assignment.dto.request.AssignmentMaxScoreRequest;
import com.tikitaka.assignment.dto.request.AssignmentUpdateRequest;
import com.tikitaka.assignment.dto.response.AssignmentCloseResponse;
import com.tikitaka.assignment.dto.response.AssignmentCreateResponse;
import com.tikitaka.assignment.dto.response.AssignmentDeleteResponse;
import com.tikitaka.assignment.dto.response.AssignmentDetailResponse;
import com.tikitaka.assignment.dto.response.AssignmentFileResponse;
import com.tikitaka.assignment.dto.response.AssignmentGradesFinalizeResponse;
import com.tikitaka.assignment.dto.response.AssignmentGradesSaveResponse;
import com.tikitaka.assignment.dto.response.AssignmentGradeUpdateResponse;
import com.tikitaka.assignment.dto.response.AssignmentListItemResponse;
import com.tikitaka.assignment.dto.response.AssignmentListResponse;
import com.tikitaka.assignment.dto.response.AssignmentMaxScoreResponse;
import com.tikitaka.assignment.dto.response.AssignmentSubmissionDownloadResponse;
import com.tikitaka.assignment.dto.response.AssignmentSubmissionListItemResponse;
import com.tikitaka.assignment.dto.response.AssignmentSubmissionListResponse;
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
import com.tikitaka.assignment.repository.AssignmentViewRepository;
import com.tikitaka.assignment.repository.SubmissionFileRepository;
import com.tikitaka.assignment.storage.AssignmentSubmissionArchiveStorage;
import com.tikitaka.notification.service.NotificationService;
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
    private final AssignmentViewRepository assignmentViewRepository;
    private final SubmissionFileRepository submissionFileRepository;
    private final AssignmentGradeRepository assignmentGradeRepository;
    private final SpaceRepository spaceRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final SpaceMemberPermissionRepository spaceMemberPermissionRepository;
    private final S3Service s3Service;
    private final AssignmentSubmissionArchiveStorage archiveStorage;
    private final NotificationService notificationService;

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

        if (member.getRole()
                == SpaceMemberRole.STUDENT) {

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

            return AssignmentSummaryResponse
                    .forStudent(
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

        return AssignmentSummaryResponse
                .forManager(
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

        boolean firstView = assignmentViewRepository.insertIfAbsent(
                currentUser.getId(),
                assignmentId
        ) == 1;

        if (firstView && assignmentRepository.increaseViewCount(assignmentId) == 0) {
            throw new BusinessException(AssignmentErrorCode.ASSIGNMENT_NOT_FOUND);
        }

        Integer viewCount = assignmentRepository.findViewCountById(assignmentId);

        List<AssignmentFileResponse> files =
                assignmentFileRepository
                        .findAllByAssignmentId(
                                assignmentId
                        )
                        .stream()
                        .map(AssignmentFileResponse::from)
                        .toList();

        AssignmentSubmissionResponse mySubmission =
                null;

        BigDecimal score =
                null;

        if (member.getRole()
                == SpaceMemberRole.STUDENT) {

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
                viewCount,
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
                        .findById(
                                spaceId
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        AssignmentErrorCode
                                                .SPACE_NOT_FOUND
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

            notificationService.createAssignmentClosedNotification(
                    assignment.getSpace(),
                    assignment.getId()
            );
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

        validateStudent(member);
        validateAssignmentOpen(assignment);

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

        validateStudent(member);
        validateAssignmentOpen(assignment);

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

    // ASG-010
    public AssignmentSubmissionListResponse getSubmissions(
            UUID assignmentId,
            User currentUser
    ) {
        Assignment assignment =
                getActiveAssignment(
                        assignmentId
                );

        SpaceMember actor =
                getApprovedMember(
                        assignment.getSpace().getId(),
                        currentUser.getId()
                );

        validateAssignmentManager(actor);

        List<SpaceMember> students =
                getApprovedStudents(
                        assignment.getSpace().getId()
                );

        Map<UUID, AssignmentSubmission> submissionByStudentId =
                assignmentSubmissionRepository
                        .findAllByAssignmentId(
                                assignmentId
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        submission ->
                                                submission.getStudent().getId(),
                                        Function.identity()
                                )
                        );

        Map<UUID, AssignmentGrade> gradeByStudentId =
                assignmentGradeRepository
                        .findAllByAssignmentId(
                                assignmentId
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        grade ->
                                                grade.getStudent().getId(),
                                        Function.identity()
                                )
                        );

        Map<UUID, List<SubmissionFile>> filesBySubmissionId =
                submissionFileRepository
                        .findAllBySubmissionAssignmentId(
                                assignmentId
                        )
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        file ->
                                                file.getSubmission().getId()
                                )
                        );

        List<AssignmentSubmissionListItemResponse> responses =
                students.stream()
                        .map(studentMember -> {

                            User student =
                                    studentMember.getUser();

                            AssignmentSubmission submission =
                                    submissionByStudentId.get(
                                            student.getId()
                                    );

                            AssignmentGrade grade =
                                    gradeByStudentId.get(
                                            student.getId()
                                    );

                            if (submission == null) {

                                return new AssignmentSubmissionListItemResponse(
                                        student.getId(),
                                        student.getName(),
                                        student.getMemberIdNumber(),
                                        "NOT_SUBMITTED",
                                        List.of(),
                                        null,
                                        grade == null
                                                ? null
                                                : grade.getScore()
                                );
                            }

                            List<AssignmentFileResponse> files =
                                    filesBySubmissionId
                                            .getOrDefault(
                                                    submission.getId(),
                                                    List.of()
                                            )
                                            .stream()
                                            .map(
                                                    AssignmentFileResponse::from
                                            )
                                            .toList();

                            return new AssignmentSubmissionListItemResponse(
                                    student.getId(),
                                    student.getName(),
                                    student.getMemberIdNumber(),
                                    submission.getStatus().name(),
                                    files,
                                    submission.getSubmittedAt(),
                                    grade == null
                                            ? null
                                            : grade.getScore()
                            );
                        })
                        .toList();

        return new AssignmentSubmissionListResponse(
                assignment.getId(),
                assignment.getMaxScore(),
                assignment.getGradingStatus().name(),
                responses
        );
    }

    // ASG-011
    public AssignmentSubmissionDownloadResponse downloadSubmissions(
            UUID assignmentId,
            User currentUser
    ) {
        Assignment assignment =
                getActiveAssignment(
                        assignmentId
                );

        SpaceMember actor =
                getApprovedMember(
                        assignment.getSpace().getId(),
                        currentUser.getId()
                );

        validateAssignmentManager(actor);

        List<SubmissionFile> submissionFiles =
                submissionFileRepository
                        .findAllBySubmissionAssignmentId(
                                assignmentId
                        );

        String downloadUrl =
                archiveStorage.createArchive(
                        assignmentId,
                        submissionFiles
                );

        return new AssignmentSubmissionDownloadResponse(
                downloadUrl
        );
    }

    // ASG-012
    @Transactional
    public AssignmentMaxScoreResponse updateMaxScore(
            UUID assignmentId,
            AssignmentMaxScoreRequest request,
            User currentUser
    ) {
        Assignment assignment =
                getActiveAssignment(
                        assignmentId
                );

        SpaceMember actor =
                getApprovedMember(
                        assignment.getSpace().getId(),
                        currentUser.getId()
                );

        validateAssignmentManager(actor);

        if (assignment.getGradingStatus()
                == GradingStatus.FINALIZED) {

            throw new BusinessException(
                    AssignmentErrorCode
                            .GRADING_ALREADY_FINALIZED
            );
        }

        BigDecimal highestScore =
                assignmentGradeRepository
                        .findAllByAssignmentId(
                                assignmentId
                        )
                        .stream()
                        .map(AssignmentGrade::getScore)
                        .filter(score ->
                                score != null
                        )
                        .max(BigDecimal::compareTo)
                        .orElse(null);

        if (highestScore != null
                && highestScore.compareTo(
                        request.maxScore()
                ) > 0) {

            throw new BusinessException(
                    AssignmentErrorCode
                            .SCORE_EXCEEDS_MAX_SCORE
            );
        }

        assignment.updateMaxScore(
                request.maxScore()
        );

        return new AssignmentMaxScoreResponse(
                assignment.getId(),
                assignment.getMaxScore()
        );
    }

    // ASG-013
    @Transactional
    public AssignmentGradesSaveResponse saveGrades(
            UUID assignmentId,
            AssignmentGradesRequest request,
            User currentUser
    ) {
        Assignment assignment =
                getActiveAssignment(
                        assignmentId
                );

        SpaceMember actor =
                getApprovedMember(
                        assignment.getSpace().getId(),
                        currentUser.getId()
                );

        validateAssignmentManager(actor);

        if (assignment.getGradingStatus()
                == GradingStatus.FINALIZED) {

            throw new BusinessException(
                    AssignmentErrorCode
                            .GRADING_ALREADY_FINALIZED
            );
        }

        List<SpaceMember> students =
                getApprovedStudents(
                        assignment.getSpace().getId()
                );

        Map<UUID, User> studentById =
                students.stream()
                        .map(SpaceMember::getUser)
                        .collect(
                                Collectors.toMap(
                                        User::getId,
                                        Function.identity()
                                )
                        );

        Set<UUID> requestStudentIds =
                new HashSet<>();

        long savedCount = 0;

        for (AssignmentGradeItemRequest gradeRequest :
                request.grades()) {

            if (!requestStudentIds.add(
                    gradeRequest.studentId()
            )) {

                continue;
            }

            User student =
                    studentById.get(
                            gradeRequest.studentId()
                    );

            if (student == null) {

                throw new BusinessException(
                        AssignmentErrorCode
                                .STUDENT_NOT_FOUND
                );
            }

            validateScore(
                    gradeRequest.score(),
                    assignment.getMaxScore()
            );

            AssignmentGrade grade =
                    assignmentGradeRepository
                            .findByAssignmentIdAndStudentId(
                                    assignmentId,
                                    student.getId()
                            )
                            .orElse(null);

            if (grade == null) {

                grade =
                        AssignmentGrade.create(
                                assignment,
                                student,
                                gradeRequest.score(),
                                currentUser
                        );

                assignmentGradeRepository.save(
                        grade
                );

            } else {

                grade.updateScore(
                        gradeRequest.score(),
                        currentUser
                );
            }

            if (gradeRequest.score() != null) {
                savedCount++;
            }
        }

        long ungradedCount =
                countUngradedStudents(
                        assignment,
                        students
                );

        return new AssignmentGradesSaveResponse(
                assignment.getId(),
                savedCount,
                ungradedCount,
                assignment.getGradingStatus().name()
        );
    }

    // ASG-014
    @Transactional
    public AssignmentGradesFinalizeResponse finalizeGrades(
            UUID assignmentId,
            User currentUser
    ) {
        Assignment assignment =
                getActiveAssignment(
                        assignmentId
                );

        SpaceMember actor =
                getApprovedMember(
                        assignment.getSpace().getId(),
                        currentUser.getId()
                );

        validateProfessor(actor);

        if (assignment.getGradingStatus()
                == GradingStatus.FINALIZED) {

            throw new BusinessException(
                    AssignmentErrorCode
                            .GRADING_ALREADY_FINALIZED
            );
        }

        List<SpaceMember> students =
                getApprovedStudents(
                        assignment.getSpace().getId()
                );

        long ungradedCount =
                countUngradedStudents(
                        assignment,
                        students
                );

        if (ungradedCount > 0) {

            throw new BusinessException(
                    AssignmentErrorCode
                            .UNGRADED_STUDENT_EXISTS
            );
        }

        assignment.finalizeGrades();

        return new AssignmentGradesFinalizeResponse(
                assignment.getId(),
                assignment.getGradingStatus().name(),
                0
        );
    }

    // ASG-015
    @Transactional
    public AssignmentGradeUpdateResponse updateFinalizedGrade(
            UUID assignmentId,
            UUID studentId,
            AssignmentGradeUpdateRequest request,
            User currentUser
    ) {
        Assignment assignment =
                getActiveAssignment(
                        assignmentId
                );

        SpaceMember actor =
                getApprovedMember(
                        assignment.getSpace().getId(),
                        currentUser.getId()
                );

        validateProfessor(actor);

        if (assignment.getGradingStatus()
                != GradingStatus.FINALIZED) {

            throw new BusinessException(
                    AssignmentErrorCode
                            .GRADING_NOT_FINALIZED
            );
        }

        User student =
                getApprovedStudents(
                        assignment.getSpace().getId()
                )
                        .stream()
                        .map(SpaceMember::getUser)
                        .filter(user ->
                                user.getId().equals(
                                        studentId
                                )
                        )
                        .findFirst()
                        .orElseThrow(() ->
                                new BusinessException(
                                        AssignmentErrorCode
                                                .STUDENT_NOT_FOUND
                                )
                        );

        validateScore(
                request.score(),
                assignment.getMaxScore()
        );

        AssignmentGrade grade =
                assignmentGradeRepository
                        .findByAssignmentIdAndStudentId(
                                assignmentId,
                                studentId
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        AssignmentErrorCode
                                                .STUDENT_NOT_FOUND
                                )
                        );

        grade.updateScore(
                request.score(),
                currentUser
        );

        return new AssignmentGradeUpdateResponse(
                assignment.getId(),
                student.getId(),
                grade.getScore(),
                assignment.getGradingStatus().name()
        );
    }

    private AssignmentListItemResponse toListItem(
            Assignment assignment,
            SpaceMember member
    ) {
        String submissionStatus =
                null;

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
        if (files == null
                || files.isEmpty()) {

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

        deleteUploadedFilesAfterRollback(
                uploaded
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
                .saveAll(entities)
                .stream()
                .map(AssignmentFileResponse::from)
                .toList();
    }

    private List<AssignmentFileResponse> uploadSubmissionFiles(
            AssignmentSubmission submission,
            List<MultipartFile> files
    ) {
        if (files == null
                || files.isEmpty()) {

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

        deleteUploadedFilesAfterRollback(
                uploaded
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
                .saveAll(entities)
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

        List<String> fileUrls =
                existingFiles.stream()
                        .map(SubmissionFile::getFileUrl)
                        .toList();

        submissionFileRepository
                .deleteAllBySubmissionId(
                        submission.getId()
                );

        submissionFileRepository.flush();

        deleteS3FilesAfterCommit(
                fileUrls
        );
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

        List<String> fileUrls =
                filesToDelete.stream()
                        .map(AssignmentFile::getFileUrl)
                        .toList();

        assignmentFileRepository.deleteAll(
                filesToDelete
        );

        assignmentFileRepository.flush();

        deleteS3FilesAfterCommit(
                fileUrls
        );
    }

    /**
     * DB COMMIT 성공 후 기존 S3 파일을 삭제한다.
     * DB rollback 시에는 afterCommit이 호출되지 않으므로
     * 기존 S3 파일이 그대로 유지된다.
     */
    private void deleteS3FilesAfterCommit(
            List<String> fileUrls
    ) {
        if (fileUrls == null
                || fileUrls.isEmpty()) {

            return;
        }

        List<String> urls =
                List.copyOf(
                        fileUrls
                );

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {

                            @Override
                            public void afterCommit() {

                                for (String fileUrl : urls) {

                                    try {

                                        s3Service
                                                .deleteByUrlIfManaged(
                                                        fileUrl
                                                );

                                    } catch (Exception ignored) {

                                        /*
                                         * DB commit은 이미 완료된 상태다.
                                         * S3 삭제 실패로 DB를 rollback할 수 없으므로
                                         * 추후 로그/재시도 대상으로 처리한다.
                                         */
                                    }
                                }
                            }
                        }
                );
    }

    /**
     * 신규 S3 업로드 이후 DB 트랜잭션이 rollback되면
     * 새로 업로드된 S3 객체를 제거한다.
     */
    private void deleteUploadedFilesAfterRollback(
            List<S3UploadResult> uploadedFiles
    ) {
        if (uploadedFiles == null
                || uploadedFiles.isEmpty()) {

            return;
        }

        List<S3UploadResult> uploaded =
                List.copyOf(
                        uploadedFiles
                );

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {

                            @Override
                            public void afterCompletion(
                                    int status
                            ) {
                                if (status
                                        != TransactionSynchronization
                                        .STATUS_ROLLED_BACK) {

                                    return;
                                }

                                for (S3UploadResult result :
                                        uploaded) {

                                    try {

                                        s3Service.delete(
                                                result.key()
                                        );

                                    } catch (Exception ignored) {

                                        /*
                                         * DB rollback은 이미 완료된 상태다.
                                         * S3 정리 실패는 별도 재시도 대상이다.
                                         */
                                    }
                                }
                            }
                        }
                );
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

    private List<SpaceMember> getApprovedStudents(
            UUID spaceId
    ) {
        return spaceMemberRepository
                .findAllBySpaceIdAndStatusAndRemovedAtIsNull(
                        spaceId,
                        SpaceMemberStatus.APPROVED
                )
                .stream()
                .filter(member ->
                        member.getRole()
                                == SpaceMemberRole.STUDENT
                )
                .sorted(
                        Comparator
                                .comparing(
                                        (SpaceMember member) ->
                                                member.getUser()
                                                        .getMemberIdNumber(),
                                        Comparator.nullsLast(Comparator.naturalOrder())
                                )
                                .thenComparing(member ->
                                        member.getUser()
                                                .getName()
                                )
                )
                .toList();
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

    private void validateProfessor(
            SpaceMember member
    ) {
        if (member.getRole()
                == SpaceMemberRole.PROFESSOR) {

            return;
        }

        throw new BusinessException(
                AssignmentErrorCode
                        .PROFESSOR_ONLY
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
                AssignmentErrorCode
                        .STUDENT_ONLY
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

    private void validateScore(
            BigDecimal score,
            BigDecimal maxScore
    ) {
        if (score == null) {
            return;
        }

        if (score.compareTo(
                BigDecimal.ZERO
        ) < 0
                || score.compareTo(
                        maxScore
                ) > 0) {

            throw new BusinessException(
                    AssignmentErrorCode
                            .SCORE_EXCEEDS_MAX_SCORE
            );
        }
    }

    private long countUngradedStudents(
            Assignment assignment,
            List<SpaceMember> students
    ) {
        Map<UUID, AssignmentGrade> grades =
                assignmentGradeRepository
                        .findAllByAssignmentId(
                                assignment.getId()
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        grade ->
                                                grade.getStudent().getId(),
                                        Function.identity()
                                )
                        );

        return students.stream()
                .filter(member -> {

                    AssignmentGrade grade =
                            grades.get(
                                    member.getUser().getId()
                            );

                    return grade == null
                            || grade.getScore() == null;
                })
                .count();
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
