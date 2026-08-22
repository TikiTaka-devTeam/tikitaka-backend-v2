package com.tikitaka.note.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.note.entity.SharedStroke;

public interface SharedStrokeRepository
        extends JpaRepository<SharedStroke, UUID> {

    List<SharedStroke>
    findAllByLayerIdAndDeletedFalseOrderByStrokeOrderAsc(
            UUID layerId
    );

    void deleteAllByLayerId(UUID layerId);
}