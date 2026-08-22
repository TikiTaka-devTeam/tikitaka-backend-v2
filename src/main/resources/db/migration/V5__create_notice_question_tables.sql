CREATE TABLE space_notices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id UUID NOT NULL,
    author_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    view_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_space_notices_space FOREIGN KEY (space_id) REFERENCES spaces(id) ON DELETE CASCADE,
    CONSTRAINT fk_space_notices_author FOREIGN KEY (author_id) REFERENCES users(id),
    CONSTRAINT ck_space_notices_view_count CHECK (view_count >= 0)
);

CREATE INDEX idx_space_notices_space_created_at ON space_notices(space_id, created_at DESC);

CREATE TABLE notice_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notice_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notice_files_notice FOREIGN KEY (notice_id) REFERENCES space_notices(id) ON DELETE CASCADE
);

CREATE INDEX idx_notice_files_notice_id ON notice_files(notice_id);

CREATE TABLE notice_reads (
    notice_id UUID NOT NULL,
    user_id UUID NOT NULL,
    read_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (notice_id, user_id),
    CONSTRAINT fk_notice_reads_notice FOREIGN KEY (notice_id) REFERENCES space_notices(id) ON DELETE CASCADE,
    CONSTRAINT fk_notice_reads_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_notice_reads_user_id ON notice_reads(user_id);

CREATE TABLE question_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_question_categories_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT fk_question_categories_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE UNIQUE INDEX uq_question_categories_active
    ON question_categories(document_id, name)
    WHERE is_deleted = FALSE;

CREATE INDEX idx_question_categories_document_active ON question_categories(document_id, is_deleted);

CREATE TABLE questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL,
    slide_id UUID,
    student_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    x_ratio DOUBLE PRECISION,
    y_ratio DOUBLE PRECISION,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    view_count INTEGER NOT NULL DEFAULT 0,
    like_count INTEGER NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_questions_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT fk_questions_slide FOREIGN KEY (slide_id) REFERENCES slides(id) ON DELETE CASCADE,
    CONSTRAINT fk_questions_student FOREIGN KEY (student_id) REFERENCES users(id),
    CONSTRAINT ck_questions_status CHECK (status IN ('PENDING', 'ANSWERED')),
    CONSTRAINT ck_questions_view_count CHECK (view_count >= 0),
    CONSTRAINT ck_questions_like_count CHECK (like_count >= 0),
    CONSTRAINT ck_questions_x_ratio CHECK (x_ratio IS NULL OR (x_ratio >= 0 AND x_ratio <= 1)),
    CONSTRAINT ck_questions_y_ratio CHECK (y_ratio IS NULL OR (y_ratio >= 0 AND y_ratio <= 1)),
    CONSTRAINT ck_questions_ping_coordinates CHECK (
        (slide_id IS NULL AND x_ratio IS NULL AND y_ratio IS NULL)
        OR
        (slide_id IS NOT NULL AND x_ratio IS NOT NULL AND y_ratio IS NOT NULL)
    )
);

CREATE INDEX idx_questions_document_created_at ON questions(document_id, created_at DESC) WHERE is_deleted = FALSE;
CREATE INDEX idx_questions_student_created_at ON questions(student_id, created_at DESC) WHERE is_deleted = FALSE;
CREATE INDEX idx_questions_slide_id ON questions(slide_id) WHERE slide_id IS NOT NULL AND is_deleted = FALSE;

CREATE TABLE question_category_mappings (
    question_id UUID NOT NULL,
    category_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (question_id, category_id),
    CONSTRAINT fk_question_category_mappings_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
    CONSTRAINT fk_question_category_mappings_category FOREIGN KEY (category_id) REFERENCES question_categories(id) ON DELETE CASCADE
);

CREATE INDEX idx_question_category_mappings_category_id ON question_category_mappings(category_id);

CREATE TABLE question_likes (
    question_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (question_id, user_id),
    CONSTRAINT fk_question_likes_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
    CONSTRAINT fk_question_likes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_question_likes_user_id ON question_likes(user_id);

CREATE TABLE answers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID NOT NULL,
    author_id UUID NOT NULL,
    content TEXT NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_answers_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
    CONSTRAINT fk_answers_author FOREIGN KEY (author_id) REFERENCES users(id)
);

CREATE INDEX idx_answers_question_created_at ON answers(question_id, created_at) WHERE is_deleted = FALSE;

CREATE TABLE question_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID NOT NULL,
    author_id UUID NOT NULL,
    parent_comment_id UUID,
    content TEXT NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_question_comments_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
    CONSTRAINT fk_question_comments_author FOREIGN KEY (author_id) REFERENCES users(id),
    CONSTRAINT fk_question_comments_parent FOREIGN KEY (parent_comment_id) REFERENCES question_comments(id) ON DELETE CASCADE
);

CREATE INDEX idx_question_comments_question_created_at ON question_comments(question_id, created_at) WHERE is_deleted = FALSE;
CREATE INDEX idx_question_comments_parent_id ON question_comments(parent_comment_id) WHERE parent_comment_id IS NOT NULL;

CREATE TABLE recent_question_views (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    question_id UUID NOT NULL,
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_recent_question_views_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_recent_question_views_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
    CONSTRAINT uq_recent_question_views UNIQUE (user_id, question_id)
);

CREATE INDEX idx_recent_question_views_user_time ON recent_question_views(user_id, viewed_at DESC);
