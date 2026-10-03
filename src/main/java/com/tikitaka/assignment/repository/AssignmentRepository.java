package com.tikitaka.assignment.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tikitaka.assignment.entity.Assignment;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    List<Assignment> findAllBySpaceIdAndDeletedFalseOrderByCreatedAtDesc(UUID spaceId);

    @Modifying
    @Query(value = """
            UPDATE assignments
            SET view_count = view_count + 1
            WHERE id = :assignmentId AND is_deleted = FALSE
            """, nativeQuery = true)
    int increaseViewCount(@Param("assignmentId") UUID assignmentId);

    @Query(value = "SELECT view_count FROM assignments WHERE id = :assignmentId", nativeQuery = true)
    Integer findViewCountById(@Param("assignmentId") UUID assignmentId);
}
