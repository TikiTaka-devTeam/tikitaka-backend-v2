package com.tikitaka.notice.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeReadId implements Serializable {

    @Column(name = "notice_id")
    private UUID noticeId;

    @Column(name = "user_id")
    private UUID userId;

    public NoticeReadId(
            UUID noticeId,
            UUID userId
    ) {
        this.noticeId = noticeId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof NoticeReadId that)) {
            return false;
        }

        return Objects.equals(noticeId, that.noticeId)
                && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(noticeId, userId);
    }
}