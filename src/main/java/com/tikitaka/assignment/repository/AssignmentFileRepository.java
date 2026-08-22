package com.tikitaka.assignment.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.assignment.entity.AssignmentFile;

public interface AssignmentFileRepository
        extends JpaRepository<AssignmentFile, UUID> {

    List<AssignmentFile> findAllByAssignmentId(UUID assignmentId);

    void deleteAllByAssignmentId(UUID assignmentId);
}