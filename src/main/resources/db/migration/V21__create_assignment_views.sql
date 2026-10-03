CREATE TABLE assignment_views (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    assignment_id UUID NOT NULL,
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_assignment_views_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_views_assignment FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE,
    CONSTRAINT uq_assignment_views UNIQUE (user_id, assignment_id)
);

CREATE INDEX idx_assignment_views_user_time ON assignment_views(user_id, viewed_at DESC);
