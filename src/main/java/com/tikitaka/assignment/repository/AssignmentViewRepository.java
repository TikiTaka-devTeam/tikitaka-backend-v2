package com.tikitaka.assignment.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tikitaka.assignment.entity.AssignmentView;

public interface AssignmentViewRepository extends JpaRepository<AssignmentView, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO assignment_views (user_id, assignment_id)
            VALUES (:userId, :assignmentId)
            ON CONFLICT (user_id, assignment_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") UUID userId,
            @Param("assignmentId") UUID assignmentId
    );
}
