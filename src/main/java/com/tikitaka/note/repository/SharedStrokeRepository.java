package com.tikitaka.note.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tikitaka.note.entity.SharedStroke;

public interface SharedStrokeRepository
        extends JpaRepository<SharedStroke, UUID> {

    @Query("""
            select s from SharedStroke s join fetch s.layer l join fetch l.slide p
            where p.document.id = :documentId and s.deleted = false
            order by p.pageNumber, s.strokeOrder, s.id
            """)
    List<SharedStroke> findForExport(@Param("documentId") UUID documentId);

    List<SharedStroke>
    findAllByLayerIdAndDeletedFalseOrderByStrokeOrderAsc(
            UUID layerId
    );

    void deleteAllByLayerId(UUID layerId);
    java.util.Optional<SharedStroke> findByIdAndLayerId(UUID id, UUID layerId);

    List<SharedStroke> findAllByLayerIdAndDeletedFalseOrderByStrokeOrderAscIdAsc(UUID layerId);
}
