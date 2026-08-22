package com.tikitaka.note.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.note.entity.PrivateStroke;

public interface PrivateStrokeRepository
        extends JpaRepository<PrivateStroke, UUID> {

    List<PrivateStroke>
    findAllByLayerIdAndDeletedFalseOrderByStrokeOrderAsc(
            UUID layerId
    );

    void deleteAllByLayerId(UUID layerId);
}