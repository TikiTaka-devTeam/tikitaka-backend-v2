package com.tikitaka.notice.entity;

import java.time.Instant;

import com.tikitaka.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notice_reads")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeRead {

    @EmbeddedId
    private NoticeReadId id;

    @MapsId("noticeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notice_id", nullable = false)
    private SpaceNotice notice;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    private NoticeRead(
            SpaceNotice notice,
            User user
    ) {
        this.id = new NoticeReadId(
                notice.getId(),
                user.getId()
        );
        this.notice = notice;
        this.user = user;
        this.readAt = Instant.now();
    }

    public static NoticeRead create(
            SpaceNotice notice,
            User user
    ) {
        return new NoticeRead(notice, user);
    }

    public void refreshReadAt() {
        this.readAt = Instant.now();
    }
}