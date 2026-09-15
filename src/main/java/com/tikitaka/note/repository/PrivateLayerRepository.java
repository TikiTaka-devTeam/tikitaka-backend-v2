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
    @org.springframework.data.jpa.repository.Query("select l from PrivateLayer l where l.slide.id = :slideId and l.user.id = :userId")
    Optional<PrivateLayer> findForRead(UUID slideId, UUID userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select l from PrivateLayer l where l.slide.id = :slideId and l.user.id = :userId")
    Optional<PrivateLayer> findForUpdate(UUID slideId, UUID userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "INSERT INTO private_layers(slide_id, user_id) VALUES (:slideId, :userId) ON CONFLICT (slide_id, user_id) DO NOTHING", nativeQuery = true)
    void ensureExists(UUID slideId, UUID userId);
}
