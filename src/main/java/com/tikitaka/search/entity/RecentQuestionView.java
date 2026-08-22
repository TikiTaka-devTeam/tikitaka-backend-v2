package com.tikitaka.search.entity;

import java.time.Instant;
import java.util.UUID;

import com.tikitaka.question.entity.Question;
import com.tikitaka.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "recent_question_views")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecentQuestionView {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;

    private RecentQuestionView(
            User user,
            Question question,
            Instant viewedAt
    ) {
        this.user = user;
        this.question = question;
        this.viewedAt = viewedAt;
    }

    public static RecentQuestionView create(
            User user,
            Question question
    ) {
        return new RecentQuestionView(
                user,
                question,
                Instant.now()
        );
    }

    public void refreshViewedAt() {
        this.viewedAt = Instant.now();
    }
}