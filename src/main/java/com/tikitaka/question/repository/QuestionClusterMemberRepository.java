package com.tikitaka.question.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.question.entity.QuestionClusterMember;

public interface QuestionClusterMemberRepository
        extends JpaRepository<QuestionClusterMember, UUID> {

    List<QuestionClusterMember>
    findAllByClusterIdOrderByCreatedAtAsc(
            UUID clusterId
    );

    Optional<QuestionClusterMember>
    findByQuestionId(
            UUID questionId
    );

    boolean existsByQuestionId(
            UUID questionId
    );
}