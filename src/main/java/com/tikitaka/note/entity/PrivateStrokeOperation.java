package com.tikitaka.note.entity;
import java.util.UUID;
import com.tikitaka.note.dto.request.StrokeSyncRequest.Payload;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
@Entity
@Table(name = "private_stroke_operations")
public class PrivateStrokeOperation extends StrokeOperationRecord {
    protected PrivateStrokeOperation() {}
    public PrivateStrokeOperation(UUID layerId, UUID operationId, Payload payload, UUID strokeId, int version) {
        super(layerId, operationId, payload, strokeId, version);
    }
}