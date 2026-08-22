CREATE TABLE spaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    professor_id UUID NOT NULL,
    space_name VARCHAR(255) NOT NULL,
    year INTEGER NOT NULL,
    semester VARCHAR(10) NOT NULL,
    classroom VARCHAR(100),
    space_code VARCHAR(8) NOT NULL,
    auto_approve BOOLEAN NOT NULL DEFAULT FALSE,
    active_status BOOLEAN NOT NULL DEFAULT TRUE,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_spaces_professor FOREIGN KEY (professor_id) REFERENCES users(id),
    CONSTRAINT uq_spaces_space_code UNIQUE (space_code),
    CONSTRAINT ck_spaces_year CHECK (year BETWEEN 2000 AND 2100)
);

CREATE INDEX idx_spaces_professor_active ON spaces(professor_id, active_status);
CREATE INDEX idx_spaces_year_semester ON spaces(year, semester);

CREATE TABLE schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id UUID NOT NULL,
    day VARCHAR(10) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_schedules_space FOREIGN KEY (space_id) REFERENCES spaces(id) ON DELETE CASCADE,
    CONSTRAINT ck_schedules_day CHECK (day IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    CONSTRAINT ck_schedules_time CHECK (start_time < end_time)
);

CREATE INDEX idx_schedules_space_id ON schedules(space_id);

CREATE TABLE space_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id UUID NOT NULL,
    user_id UUID NOT NULL,
    color_key VARCHAR(20) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    approved_at TIMESTAMPTZ,
    denied_at TIMESTAMPTZ,
    removed_at TIMESTAMPTZ,
    last_accessed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_space_members_space FOREIGN KEY (space_id) REFERENCES spaces(id) ON DELETE CASCADE,
    CONSTRAINT fk_space_members_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_space_members_color CHECK (color_key IN ('COLOR_1','COLOR_2','COLOR_3','COLOR_4','COLOR_5','COLOR_6','COLOR_7','COLOR_8','COLOR_9','COLOR_10','COLOR_11','COLOR_12')),
    CONSTRAINT ck_space_members_role CHECK (role IN ('PROFESSOR', 'ASSISTANT', 'STUDENT')),
    CONSTRAINT ck_space_members_status CHECK (status IN ('PENDING', 'APPROVED', 'DENIED', 'REMOVED'))
);

CREATE UNIQUE INDEX uq_space_members_active
    ON space_members(space_id, user_id)
    WHERE status IN ('PENDING', 'APPROVED');

CREATE INDEX idx_space_members_user_status ON space_members(user_id, status);
CREATE INDEX idx_space_members_space_status ON space_members(space_id, status);

CREATE TABLE space_member_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_member_id UUID NOT NULL,
    permission VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_space_member_permissions_member FOREIGN KEY (space_member_id) REFERENCES space_members(id) ON DELETE CASCADE,
    CONSTRAINT uq_space_member_permissions UNIQUE (space_member_id, permission),
    CONSTRAINT ck_space_member_permissions_permission CHECK (permission IN (
        'MEMBER_MANAGE',
        'LECTURE_MATERIAL_MANAGE',
        'NOTICE_MANAGE',
        'QUESTION_MANAGE',
        'ASSIGNMENT_MANAGE'
    ))
);

CREATE INDEX idx_space_member_permissions_member ON space_member_permissions(space_member_id);

CREATE TABLE recent_searches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    keyword VARCHAR(255) NOT NULL,
    searched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_recent_searches_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_recent_searches_user_keyword UNIQUE (user_id, keyword)
);

CREATE INDEX idx_recent_searches_user_time ON recent_searches(user_id, searched_at DESC);
