package com.tikitaka.question.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.question.entity.QuestionComment;

public interface QuestionCommentRepository
        extends JpaRepository<QuestionComment, UUID> {

    List<QuestionComment>
    findAllByQuestionIdAndDeletedFalseOrderByCreatedAtAsc(
            UUID questionId
    );
}