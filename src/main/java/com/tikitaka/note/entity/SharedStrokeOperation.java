package com.tikitaka.note.entity;
import java.util.UUID;
import com.tikitaka.note.dto.request.StrokeSyncRequest.Payload;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
@Entity
@Table(name = "shared_stroke_operations")
public class SharedStrokeOperation extends StrokeOperationRecord {
    protected SharedStrokeOperation() {}
    public SharedStrokeOperation(UUID layerId, UUID operationId, Payload payload, UUID strokeId, int version) {
        super(layerId, operationId, payload, strokeId, version);
    }
}