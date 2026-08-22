package com.tikitaka.search.entity;

import java.time.Instant;
import java.util.UUID;

import com.tikitaka.document.entity.Document;
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
@Table(name = "recent_document_views")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecentDocumentView {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;

    private RecentDocumentView(
            User user,
            Document document,
            Instant viewedAt
    ) {
        this.user = user;
        this.document = document;
        this.viewedAt = viewedAt;
    }

    public static RecentDocumentView create(
            User user,
            Document document
    ) {
        return new RecentDocumentView(
                user,
                document,
                Instant.now()
        );
    }

    public void refreshViewedAt() {
        this.viewedAt = Instant.now();
    }
}