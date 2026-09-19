package com.tikitaka.question.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tikitaka.question.entity.Question;
import com.tikitaka.question.entity.QuestionStatus;
import com.tikitaka.question.repository.projection.QuestionSimilarityProjection;

import jakarta.persistence.LockModeType;

public interface QuestionRepository
        extends JpaRepository<Question, UUID> {

    List<Question> findAllByDocumentIdAndDeletedFalse(
            UUID documentId
    );

    List<Question> findAllByDocumentSpaceIdAndDeletedFalse(
            UUID spaceId
    );

    List<Question> findAllByDocumentSpaceIdAndStudentIdAndDeletedFalse(
            UUID spaceId,
            UUID studentId
    );

    List<Question> findAllByStudentIdAndDeletedFalse(
            UUID studentId
    );

    List<Question> findAllByDocumentIdAndStatusAndDeletedFalse(
            UUID documentId,
            QuestionStatus status
    );

    List<Question> findAllBySlideIdAndDeletedFalse(
            UUID slideId
    );

    long countByStudentIdAndStatusAndDeletedFalse(
            UUID studentId,
            QuestionStatus status
    );

    long countByDocumentSpaceIdAndStudentIdAndDeletedFalse(
            UUID spaceId,
            UUID studentId
    );

    boolean existsByPrimaryCategoryIdAndDeletedFalse(
        UUID primaryCategoryId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Question> findQuestionById(
            UUID id
    );

    @Query(
            value = """
                    SELECT
                        q.id AS questionId,
                        1 - (
                            q.embedding <=> CAST(:embedding AS vector)
                        ) AS similarity
                    FROM questions q
                    WHERE q.document_id = :documentId
                      AND q.is_deleted = false
                      AND q.embedding IS NOT NULL
                      AND q.question_scope = 'COURSE_RELATED'
                    ORDER BY
                        q.embedding <=> CAST(:embedding AS vector)
                    LIMIT 5
                    """,
            nativeQuery = true
    )
    List<QuestionSimilarityProjection> findSimilarQuestions(
            @Param("documentId") UUID documentId,
            @Param("embedding") String embedding
    );
}