package com.tikitaka.note.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.note.entity.SharedLayer;

public interface SharedLayerRepository
        extends JpaRepository<SharedLayer, UUID> {

    Optional<SharedLayer> findBySlideId(UUID slideId);

    boolean existsBySlideId(UUID slideId);

    void deleteBySlideId(UUID slideId);
}