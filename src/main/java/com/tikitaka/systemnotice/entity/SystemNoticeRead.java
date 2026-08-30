package com.tikitaka.systemnotice.entity;

import java.time.Instant;
import com.tikitaka.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Entity
@Table(name = "system_notice_reads")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemNoticeRead {
    @EmbeddedId private SystemNoticeReadId id;
    @MapsId("systemNoticeId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "system_notice_id", nullable = false) private SystemNotice systemNotice;
    @MapsId("userId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "read_at", nullable = false) private Instant readAt;
    private SystemNoticeRead(SystemNotice systemNotice, User user){
        this.id=new SystemNoticeReadId(systemNotice.getId(),user.getId()); this.systemNotice=systemNotice; this.user=user; this.readAt=Instant.now();
    }
    public static SystemNoticeRead create(SystemNotice systemNotice, User user){ return new SystemNoticeRead(systemNotice,user); }
}
