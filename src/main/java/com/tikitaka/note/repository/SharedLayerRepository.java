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
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("select l from SharedLayer l where l.slide.id = :slideId")
    Optional<SharedLayer> findForRead(UUID slideId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select l from SharedLayer l where l.slide.id = :slideId")
    Optional<SharedLayer> findForUpdate(UUID slideId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "INSERT INTO shared_layers(slide_id) VALUES (:slideId) ON CONFLICT (slide_id) DO NOTHING", nativeQuery = true)
    void ensureExists(UUID slideId);
}
