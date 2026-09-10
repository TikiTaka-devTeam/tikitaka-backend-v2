package com.tikitaka.document.entity;

import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tikitaka.global.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "revision_operations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RevisionOperation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    private DocumentRevision revision;

    @Column(name = "client_operation_id", nullable = false)
    private UUID clientOperationId;

    @Column(nullable = false)
    private Integer sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RevisionOperationType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "inverse_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> inversePayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RevisionOperationState state = RevisionOperationState.APPLIED;

    @Column(name = "preview_version", nullable = false)
    private Integer previewVersion;

    private RevisionOperation(
            DocumentRevision revision,
            UUID clientOperationId,
            Integer sequence,
            RevisionOperationType type,
            Map<String, Object> payload,
            Map<String, Object> inversePayload,
            Integer previewVersion
    ) {
        this.revision = revision;
        this.clientOperationId = clientOperationId;
        this.sequence = sequence;
        this.type = type;
        this.payload = Map.copyOf(payload);
        this.inversePayload = Map.copyOf(inversePayload);
        this.state = RevisionOperationState.APPLIED;
        this.previewVersion = previewVersion;
    }

    public static RevisionOperation create(
            DocumentRevision revision,
            UUID clientOperationId,
            Integer sequence,
            RevisionOperationType type,
            Map<String, Object> payload,
            Map<String, Object> inversePayload,
            Integer previewVersion
    ) {
        return new RevisionOperation(
                revision,
                clientOperationId,
                sequence,
                type,
                payload,
                inversePayload,
                previewVersion);
    }

    public void undo() {
        this.state = RevisionOperationState.UNDONE;
    }

    public void redo() {
        this.state = RevisionOperationState.APPLIED;
    }

    public void discard() {
        this.state = RevisionOperationState.DISCARDED;
    }
}
