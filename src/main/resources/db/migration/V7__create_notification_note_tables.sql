CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    space_id UUID,
    type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    target_id UUID,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_space FOREIGN KEY (space_id) REFERENCES spaces(id) ON DELETE CASCADE,
    CONSTRAINT ck_notifications_read_state CHECK (
        (is_read = FALSE AND read_at IS NULL)
        OR
        (is_read = TRUE AND read_at IS NOT NULL)
    )
);

CREATE INDEX idx_notifications_user_created_at ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, created_at DESC) WHERE is_read = FALSE;

CREATE TABLE private_layers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slide_id UUID NOT NULL,
    user_id UUID NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_private_layers_slide FOREIGN KEY (slide_id) REFERENCES slides(id) ON DELETE CASCADE,
    CONSTRAINT fk_private_layers_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_private_layers UNIQUE (slide_id, user_id),
    CONSTRAINT ck_private_layers_version CHECK (version >= 0)
);

CREATE INDEX idx_private_layers_user_id ON private_layers(user_id);

CREATE TABLE private_strokes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    layer_id UUID NOT NULL,
    tool VARCHAR(30) NOT NULL,
    points JSONB,
    content TEXT,
    color VARCHAR(20),
    thickness DOUBLE PRECISION,
    opacity DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    stroke_order INTEGER NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_private_strokes_layer FOREIGN KEY (layer_id) REFERENCES private_layers(id) ON DELETE CASCADE,
    CONSTRAINT ck_private_strokes_thickness CHECK (thickness IS NULL OR thickness > 0),
    CONSTRAINT ck_private_strokes_opacity CHECK (opacity >= 0 AND opacity <= 1),
    CONSTRAINT ck_private_strokes_order CHECK (stroke_order >= 0),
    CONSTRAINT ck_private_strokes_content CHECK (points IS NOT NULL OR content IS NOT NULL)
);

CREATE INDEX idx_private_strokes_layer_order ON private_strokes(layer_id, stroke_order) WHERE is_deleted = FALSE;

CREATE TABLE shared_layers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slide_id UUID NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_shared_layers_slide FOREIGN KEY (slide_id) REFERENCES slides(id) ON DELETE CASCADE,
    CONSTRAINT uq_shared_layers_slide UNIQUE (slide_id),
    CONSTRAINT ck_shared_layers_version CHECK (version >= 0)
);

CREATE TABLE shared_strokes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    layer_id UUID NOT NULL,
    tool VARCHAR(30) NOT NULL,
    points JSONB,
    content TEXT,
    color VARCHAR(20),
    thickness DOUBLE PRECISION,
    opacity DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    stroke_order INTEGER NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_shared_strokes_layer FOREIGN KEY (layer_id) REFERENCES shared_layers(id) ON DELETE CASCADE,
    CONSTRAINT ck_shared_strokes_thickness CHECK (thickness IS NULL OR thickness > 0),
    CONSTRAINT ck_shared_strokes_opacity CHECK (opacity >= 0 AND opacity <= 1),
    CONSTRAINT ck_shared_strokes_order CHECK (stroke_order >= 0),
    CONSTRAINT ck_shared_strokes_content CHECK (points IS NOT NULL OR content IS NOT NULL)
);

CREATE INDEX idx_shared_strokes_layer_order ON shared_strokes(layer_id, stroke_order) WHERE is_deleted = FALSE;

CREATE TABLE fixers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slide_id UUID NOT NULL,
    professor_id UUID NOT NULL,
    x_ratio DOUBLE PRECISION NOT NULL,
    y_ratio DOUBLE PRECISION NOT NULL,
    content TEXT NOT NULL,
    is_checked BOOLEAN NOT NULL DEFAULT FALSE,
    checked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_fixers_slide FOREIGN KEY (slide_id) REFERENCES slides(id) ON DELETE CASCADE,
    CONSTRAINT fk_fixers_professor FOREIGN KEY (professor_id) REFERENCES users(id),
    CONSTRAINT ck_fixers_x_ratio CHECK (x_ratio >= 0 AND x_ratio <= 1),
    CONSTRAINT ck_fixers_y_ratio CHECK (y_ratio >= 0 AND y_ratio <= 1),
    CONSTRAINT ck_fixers_checked_state CHECK (
        (is_checked = FALSE AND checked_at IS NULL)
        OR
        (is_checked = TRUE AND checked_at IS NOT NULL)
    )
);

CREATE INDEX idx_fixers_professor_checked ON fixers(professor_id, is_checked);
CREATE INDEX idx_fixers_slide_id ON fixers(slide_id);
