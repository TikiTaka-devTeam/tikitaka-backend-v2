CREATE TABLE system_notices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    is_important BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_system_notices_created_at ON system_notices(created_at DESC, id DESC);

CREATE TABLE system_notice_reads (
    system_notice_id UUID NOT NULL,
    user_id UUID NOT NULL,
    read_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (system_notice_id, user_id),
    CONSTRAINT fk_system_notice_reads_notice FOREIGN KEY (system_notice_id) REFERENCES system_notices(id) ON DELETE CASCADE,
    CONSTRAINT fk_system_notice_reads_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_system_notice_reads_user_id ON system_notice_reads(user_id);
