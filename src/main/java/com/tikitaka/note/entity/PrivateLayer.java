package com.tikitaka.note.entity;

import java.util.UUID;

import com.tikitaka.document.entity.Slide;
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
@Table(name = "private_layers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrivateLayer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slide_id", nullable = false)
    private Slide slide;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer version = 0;

    private PrivateLayer(
            Slide slide,
            User user
    ) {
        this.slide = slide;
        this.user = user;
        this.version = 0;
    }

    public static PrivateLayer create(
            Slide slide,
            User user
    ) {
        return new PrivateLayer(slide, user);
    }

    public void increaseVersion() {
        this.version++;
    }
}