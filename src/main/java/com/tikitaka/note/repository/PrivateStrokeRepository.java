package com.tikitaka.note.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tikitaka.note.entity.PrivateStroke;

public interface PrivateStrokeRepository
        extends JpaRepository<PrivateStroke, UUID> {

    @Query("""
            select s from PrivateStroke s join fetch s.layer l join fetch l.slide p
            where p.document.id = :documentId and l.user.id = :userId and s.deleted = false
            order by p.pageNumber, s.strokeOrder, s.id
            """)
    List<PrivateStroke> findForExport(@Param("documentId") UUID documentId,
            @Param("userId") UUID userId);

    List<PrivateStroke>
    findAllByLayerIdAndDeletedFalseOrderByStrokeOrderAsc(
            UUID layerId
    );

    void deleteAllByLayerId(UUID layerId);
    java.util.Optional<PrivateStroke> findByIdAndLayerId(UUID id, UUID layerId);

    List<PrivateStroke> findAllByLayerIdAndDeletedFalseOrderByStrokeOrderAscIdAsc(UUID layerId);
}
