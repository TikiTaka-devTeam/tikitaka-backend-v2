package com.tikitaka.assignment.entity;

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
@Table(name = "assignment_submissions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssignmentSubmission extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(nullable = false)
    private Integer version = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    private AssignmentSubmission(
            Assignment assignment,
            User student,
            String comment,
            SubmissionStatus status
    ) {
        this.assignment = assignment;
        this.student = student;
        this.comment = comment;
        this.status = status;
        this.version = 1;
        this.submittedAt = Instant.now();
    }

    public static AssignmentSubmission create(
            Assignment assignment,
            User student,
            String comment,
            SubmissionStatus status
    ) {
        return new AssignmentSubmission(
                assignment,
                student,
                comment,
                status
        );
    }

    public void resubmit(
            String comment,
            SubmissionStatus status
    ) {
        this.comment = comment;
        this.status = status;
        this.version++;
        this.submittedAt = Instant.now();
    }
}