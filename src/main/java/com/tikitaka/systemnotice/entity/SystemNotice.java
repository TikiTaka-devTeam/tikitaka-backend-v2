package com.tikitaka.systemnotice.entity;

import java.util.UUID;
import com.tikitaka.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Entity
@Table(name = "system_notices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemNotice extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 255) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(name = "is_important", nullable = false) private boolean important;

    private SystemNotice(String title, String content, boolean important) {
        this.title = title; this.content = content; this.important = important;
    }
    public static SystemNotice create(String title, String content, boolean important) {
        return new SystemNotice(title, content, important);
    }
}
