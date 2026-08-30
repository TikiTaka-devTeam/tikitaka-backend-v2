package com.tikitaka.systemnotice.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.*;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemNoticeReadId implements Serializable {
    @Column(name = "system_notice_id") private UUID systemNoticeId;
    @Column(name = "user_id") private UUID userId;
    public SystemNoticeReadId(UUID systemNoticeId, UUID userId) { this.systemNoticeId=systemNoticeId; this.userId=userId; }
    @Override public boolean equals(Object o){ if(this==o)return true; if(!(o instanceof SystemNoticeReadId that))return false; return Objects.equals(systemNoticeId,that.systemNoticeId)&&Objects.equals(userId,that.userId); }
    @Override public int hashCode(){ return Objects.hash(systemNoticeId,userId); }
}
