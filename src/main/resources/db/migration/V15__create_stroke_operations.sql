-- Keep operation references scoped to their layer.
ALTER TABLE private_strokes ADD CONSTRAINT uq_private_strokes_layer_id UNIQUE (layer_id, id);
ALTER TABLE shared_strokes ADD CONSTRAINT uq_shared_strokes_layer_id UNIQUE (layer_id, id);
CREATE TABLE private_stroke_operations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    layer_id UUID NOT NULL REFERENCES private_layers(id) ON DELETE CASCADE,
    client_operation_id UUID NOT NULL,
    operation_type VARCHAR(20) NOT NULL CHECK (operation_type IN ('CREATE', 'DELETE')),
    request_payload JSONB NOT NULL,
    client_stroke_id UUID,
    stroke_id UUID NOT NULL,
    applied_version INTEGER NOT NULL CHECK (applied_version >= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_private_stroke_operations_operation UNIQUE (layer_id, client_operation_id),
    CONSTRAINT ck_private_stroke_operations_client_stroke CHECK (
        (operation_type = 'CREATE' AND client_stroke_id IS NOT NULL) OR
        (operation_type = 'DELETE' AND client_stroke_id IS NULL)
    ),
    CONSTRAINT fk_private_stroke_operations_stroke FOREIGN KEY (layer_id, stroke_id)
        REFERENCES private_strokes(layer_id, id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED
);
CREATE UNIQUE INDEX uq_private_stroke_operations_client_stroke
    ON private_stroke_operations(layer_id, client_stroke_id) WHERE operation_type = 'CREATE';
CREATE TABLE shared_stroke_operations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    layer_id UUID NOT NULL REFERENCES shared_layers(id) ON DELETE CASCADE,
    client_operation_id UUID NOT NULL,
    operation_type VARCHAR(20) NOT NULL CHECK (operation_type IN ('CREATE', 'DELETE')),
    request_payload JSONB NOT NULL,
    client_stroke_id UUID,
    stroke_id UUID NOT NULL,
    applied_version INTEGER NOT NULL CHECK (applied_version >= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_shared_stroke_operations_operation UNIQUE (layer_id, client_operation_id),
    CONSTRAINT ck_shared_stroke_operations_client_stroke CHECK (
        (operation_type = 'CREATE' AND client_stroke_id IS NOT NULL) OR
        (operation_type = 'DELETE' AND client_stroke_id IS NULL)
    ),
    CONSTRAINT fk_shared_stroke_operations_stroke FOREIGN KEY (layer_id, stroke_id)
        REFERENCES shared_strokes(layer_id, id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED
);
CREATE UNIQUE INDEX uq_shared_stroke_operations_client_stroke
    ON shared_stroke_operations(layer_id, client_stroke_id) WHERE operation_type = 'CREATE';