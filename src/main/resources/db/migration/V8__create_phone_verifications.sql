CREATE TABLE phone_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number VARCHAR(11) NOT NULL,
    request_ip INET NOT NULL,
    verification_code_hash VARCHAR(64) NOT NULL,
    code_expires_at TIMESTAMPTZ NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    resend_available_at TIMESTAMPTZ NOT NULL,
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    verified_at TIMESTAMPTZ,
    verification_token_hash VARCHAR(64),
    token_expires_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_phone_verifications_token_hash UNIQUE (verification_token_hash),
    CONSTRAINT ck_phone_verifications_phone_number CHECK (phone_number ~ '^01[016789][0-9]{7,8}$'),
    CONSTRAINT ck_phone_verifications_attempt_count CHECK (attempt_count BETWEEN 0 AND 5),
    CONSTRAINT ck_phone_verifications_delivery_status
        CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_phone_verifications_phone_created_at
    ON phone_verifications(phone_number, created_at DESC);

CREATE INDEX idx_phone_verifications_ip_created_at
    ON phone_verifications(request_ip, created_at DESC);

CREATE INDEX idx_phone_verifications_phone_code_expires_at
    ON phone_verifications(phone_number, code_expires_at DESC);

CREATE INDEX idx_phone_verifications_created_at
    ON phone_verifications(created_at);
