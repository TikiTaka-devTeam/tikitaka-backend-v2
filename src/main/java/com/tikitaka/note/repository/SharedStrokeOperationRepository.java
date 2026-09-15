package com.tikitaka.note.repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.tikitaka.note.entity.SharedStrokeOperation;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SharedStrokeOperationRepository extends JpaRepository<SharedStrokeOperation, UUID> {
    List<SharedStrokeOperation> findAllByLayerIdAndClientOperationIdIn(UUID layerId, List<UUID> operationIds);
    boolean existsByLayerIdAndClientStrokeId(UUID layerId, UUID clientStrokeId);
}