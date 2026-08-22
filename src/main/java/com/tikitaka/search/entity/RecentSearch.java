package com.tikitaka.search.entity;

import java.time.Instant;
import java.util.UUID;

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
@Table(name = "recent_searches")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecentSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String keyword;

    @Column(name = "searched_at", nullable = false)
    private Instant searchedAt;

    private RecentSearch(
            User user,
            String keyword,
            Instant searchedAt
    ) {
        this.user = user;
        this.keyword = keyword;
        this.searchedAt = searchedAt;
    }

    public static RecentSearch create(
            User user,
            String keyword
    ) {
        return new RecentSearch(
                user,
                keyword,
                Instant.now()
        );
    }

    public void refreshSearchedAt() {
        this.searchedAt = Instant.now();
    }
}