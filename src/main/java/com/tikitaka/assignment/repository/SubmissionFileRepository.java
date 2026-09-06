package com.tikitaka.assignment.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.assignment.entity.SubmissionFile;

public interface SubmissionFileRepository
        extends JpaRepository<SubmissionFile, UUID> {

    List<SubmissionFile> findAllBySubmissionId(
            UUID submissionId
    );

    List<SubmissionFile> findAllBySubmissionAssignmentId(
            UUID assignmentId
    );

    void deleteAllBySubmissionId(
            UUID submissionId
    );
}