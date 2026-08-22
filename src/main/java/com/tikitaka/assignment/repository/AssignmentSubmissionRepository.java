package com.tikitaka.assignment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.assignment.entity.AssignmentSubmission;

public interface AssignmentSubmissionRepository
        extends JpaRepository<AssignmentSubmission, UUID> {

    Optional<AssignmentSubmission> findByAssignmentIdAndStudentId(
            UUID assignmentId,
            UUID studentId
    );

    List<AssignmentSubmission> findAllByAssignmentId(
            UUID assignmentId
    );

    boolean existsByAssignmentIdAndStudentId(
            UUID assignmentId,
            UUID studentId
    );
}