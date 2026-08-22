package com.tikitaka.space.entity;

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
@Table(name = "space_members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpaceMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private Space space;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "color_key", nullable = false, length = 20)
    private SpaceColorKey colorKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpaceMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpaceMemberStatus status = SpaceMemberStatus.PENDING;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "denied_at")
    private Instant deniedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;
}