package com.tikitaka.space.entity;

import java.time.Instant;
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
@Table(name = "spaces")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Space extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professor_id", nullable = false)
    private User professor;

    @Column(name = "space_name", nullable = false, length = 255)
    private String spaceName;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false, length = 10)
    private String semester;

    @Column(length = 100)
    private String classroom;

    @Column(name = "space_code", nullable = false, unique = true, length = 8)
    private String spaceCode;

    @Column(name = "auto_approve", nullable = false)
    private boolean autoApprove = false;

    @Column(name = "active_status", nullable = false)
    private boolean activeStatus = true;

    @Column(name = "archived_at")
    private Instant archivedAt;
}