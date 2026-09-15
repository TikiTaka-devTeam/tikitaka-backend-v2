package com.tikitaka.note.entity;

import java.time.Instant;
import java.util.UUID;
import com.tikitaka.note.dto.request.StrokeSyncRequest.Payload;
import com.tikitaka.note.dto.request.StrokeSyncRequest.Type;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@MappedSuperclass
public abstract class StrokeOperationRecord {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "layer_id", nullable = false)
    private UUID layerId;
    @Column(name = "client_operation_id", nullable = false)
    private UUID clientOperationId;
    @Enumerated(EnumType.STRING) @Column(name = "operation_type", nullable = false, length = 20)
    private Type operationType;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "request_payload", nullable = false, columnDefinition = "jsonb")
    private Payload requestPayload;
    @Column(name = "client_stroke_id")
    private UUID clientStrokeId;
    @Column(name = "stroke_id", nullable = false)
    private UUID strokeId;
    @Column(name = "applied_version", nullable = false)
    private Integer appliedVersion;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StrokeOperationRecord() {}
    protected StrokeOperationRecord(UUID layerId, UUID operationId, Payload payload, UUID strokeId, int version) {
        this.layerId = layerId;
        this.clientOperationId = operationId;
        this.operationType = payload.type();
        this.requestPayload = payload;
        this.clientStrokeId = payload.stroke() == null ? null : payload.stroke().clientStrokeId();
        this.strokeId = strokeId;
        this.appliedVersion = version;
        this.createdAt = Instant.now();
    }
}