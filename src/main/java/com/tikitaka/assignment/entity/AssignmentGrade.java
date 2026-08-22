package com.tikitaka.assignment.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.tikitaka.global.common.entity.BaseTimeEntity;
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
@Table(name = "assignment_grades")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssignmentGrade extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(precision = 6, scale = 2)
    private BigDecimal score;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "graded_by", nullable = false)
    private User gradedBy;

    private AssignmentGrade(
            Assignment assignment,
            User student,
            BigDecimal score,
            User gradedBy
    ) {
        this.assignment = assignment;
        this.student = student;
        this.score = score;
        this.gradedBy = gradedBy;
    }

    public static AssignmentGrade create(
            Assignment assignment,
            User student,
            BigDecimal score,
            User gradedBy
    ) {
        return new AssignmentGrade(
                assignment,
                student,
                score,
                gradedBy
        );
    }

    public void updateScore(
            BigDecimal score,
            User gradedBy
    ) {
        this.score = score;
        this.gradedBy = gradedBy;
    }
}