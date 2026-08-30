package com.tikitaka.assignment.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.assignment.entity.Assignment;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    List<Assignment> findAllBySpaceIdAndDeletedFalseOrderByCreatedAtDesc(UUID spaceId);
}
