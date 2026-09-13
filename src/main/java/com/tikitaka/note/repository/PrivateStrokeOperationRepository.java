package com.tikitaka.note.repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.tikitaka.note.entity.PrivateStrokeOperation;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PrivateStrokeOperationRepository extends JpaRepository<PrivateStrokeOperation, UUID> {
    List<PrivateStrokeOperation> findAllByLayerIdAndClientOperationIdIn(UUID layerId, List<UUID> operationIds);
    boolean existsByLayerIdAndClientStrokeId(UUID layerId, UUID clientStrokeId);
}