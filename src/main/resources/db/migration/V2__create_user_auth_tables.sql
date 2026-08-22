CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(100) NOT NULL,
    password VARCHAR(255),
    name VARCHAR(30) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    phone_number VARCHAR(20),
    univ VARCHAR(100) NOT NULL,
    major VARCHAR(100) NOT NULL,
    member_id_number VARCHAR(30) NOT NULL,
    profile_url TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_phone_number UNIQUE (phone_number),
    CONSTRAINT ck_users_account_type CHECK (account_type IN ('PROFESSOR', 'STUDENT')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'WITHDRAWN', 'RESTRICTED'))
);

CREATE TABLE auth (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_auth_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_auth_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT ck_auth_provider CHECK (provider IN ('KAKAO', 'GOOGLE'))
);

CREATE INDEX idx_auth_user_id ON auth(user_id);

CREATE TABLE tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    refresh_token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_tokens_refresh_token_hash UNIQUE (refresh_token_hash)
);

CREATE INDEX idx_tokens_user_id ON tokens(user_id);
CREATE INDEX idx_tokens_user_active ON tokens(user_id, expires_at) WHERE revoked_at IS NULL;

CREATE TABLE inquiries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_inquiries_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_inquiries_user_created_at ON inquiries(user_id, created_at DESC);
