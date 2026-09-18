package com.tikitaka.question.entity;

import java.time.Instant;
import java.util.UUID;

import com.tikitaka.global.common.entity.BaseTimeEntity;
import com.tikitaka.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "answers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Answer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "answer_type",
            nullable = false,
            length = 20
    )
    private AnswerType answerType =
            AnswerType.TEXT;

    @Column(
            name = "audio_url",
            columnDefinition = "text"
    )
    private String audioUrl;

    @Column(
            name = "transcript",
            columnDefinition = "text"
    )
    private String transcript;

    @Column(
            name = "is_deleted",
            nullable = false
    )
    private boolean deleted = false;

    @Column(
            name = "deleted_at"
    )
    private Instant deletedAt;

    private Answer(
            Question question,
            User author,
            String content,
            AnswerType answerType,
            String audioUrl,
            String transcript
    ) {
        this.question = question;
        this.author = author;
        this.content = content;
        this.answerType = answerType;
        this.audioUrl = audioUrl;
        this.transcript = transcript;
    }

    public static Answer create(
            Question question,
            User author,
            String content
    ) {
        return createText(
                question,
                author,
                content
        );
    }

    public static Answer createText(
            Question question,
            User author,
            String content
    ) {
        return new Answer(
                question,
                author,
                content,
                AnswerType.TEXT,
                null,
                null
        );
    }

    public static Answer createVoice(
            Question question,
            User author,
            String content,
            String audioUrl,
            String transcript
    ) {
        return new Answer(
                question,
                author,
                content,
                AnswerType.VOICE,
                audioUrl,
                transcript
        );
    }

    public void updateContent(
            String content
    ) {
        this.content = content;
    }

    public void delete() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}