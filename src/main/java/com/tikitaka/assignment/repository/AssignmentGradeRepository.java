package com.tikitaka.assignment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.assignment.entity.AssignmentGrade;

public interface AssignmentGradeRepository
        extends JpaRepository<AssignmentGrade, UUID> {

    Optional<AssignmentGrade> findByAssignmentIdAndStudentId(
            UUID assignmentId,
            UUID studentId
    );

    List<AssignmentGrade> findAllByAssignmentId(
            UUID assignmentId
    );

    long countByAssignmentIdAndScoreIsNull(UUID assignmentId);
}