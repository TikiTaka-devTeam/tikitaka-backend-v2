package com.tikitaka.note.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.note.entity.PrivateLayer;

public interface PrivateLayerRepository
        extends JpaRepository<PrivateLayer, UUID> {

    Optional<PrivateLayer> findBySlideIdAndUserId(
            UUID slideId,
            UUID userId
    );

    boolean existsBySlideIdAndUserId(
            UUID slideId,
            UUID userId
    );

    void deleteAllBySlideId(UUID slideId);
}