package com.tikitaka.note.entity;

import java.util.UUID;

import com.tikitaka.document.entity.Slide;
import com.tikitaka.global.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "shared_layers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharedLayer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slide_id", nullable = false, unique = true)
    private Slide slide;

    @Column(nullable = false)
    private Integer version = 0;

    private SharedLayer(Slide slide) {
        this.slide = slide;
        this.version = 0;
    }

    public static SharedLayer create(Slide slide) {
        return new SharedLayer(slide);
    }

    public void increaseVersion() {
        this.version++;
    }
}