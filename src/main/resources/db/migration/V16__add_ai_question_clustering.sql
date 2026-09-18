-- AI question classification / clustering support

ALTER TABLE question_categories
    ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN source_pages JSONB;

ALTER TABLE question_categories
    ADD CONSTRAINT ck_question_categories_source_type
    CHECK (source_type IN ('MANUAL', 'AI'));


ALTER TABLE questions
    ADD COLUMN question_scope VARCHAR(20),
    ADD COLUMN primary_category_id UUID,
    ADD COLUMN embedding VECTOR(768),
    ADD COLUMN ai_processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN ai_processed_at TIMESTAMPTZ;

ALTER TABLE questions
    ADD CONSTRAINT fk_questions_primary_category
    FOREIGN KEY (primary_category_id)
    REFERENCES question_categories(id);

ALTER TABLE questions
    ADD CONSTRAINT ck_questions_question_scope
    CHECK (
        question_scope IS NULL
        OR question_scope IN ('COURSE_RELATED', 'OTHER')
    );

ALTER TABLE questions
    ADD CONSTRAINT ck_questions_ai_processing_status
    CHECK (
        ai_processing_status IN (
            'PENDING',
            'PROCESSING',
            'COMPLETED',
            'FAILED'
        )
    );


CREATE TABLE question_clusters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    document_id UUID NOT NULL,
    category_id UUID NOT NULL,

    summary_title VARCHAR(255) NOT NULL,
    centroid VECTOR(768) NOT NULL,

    member_count INTEGER NOT NULL DEFAULT 1,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_question_clusters_document
        FOREIGN KEY (document_id)
        REFERENCES documents(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_question_clusters_category
        FOREIGN KEY (category_id)
        REFERENCES question_categories(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_question_clusters_member_count
        CHECK (member_count >= 0)
);

CREATE INDEX idx_question_clusters_document_category
    ON question_clusters(document_id, category_id);


CREATE TABLE question_cluster_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    cluster_id UUID NOT NULL,
    question_id UUID NOT NULL,

    similarity REAL NOT NULL,
    is_representative BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_question_cluster_members_cluster
        FOREIGN KEY (cluster_id)
        REFERENCES question_clusters(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_question_cluster_members_question
        FOREIGN KEY (question_id)
        REFERENCES questions(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_question_cluster_members_similarity
        CHECK (similarity >= 0 AND similarity <= 1),

    CONSTRAINT uq_question_cluster_members_cluster_question
        UNIQUE (cluster_id, question_id),

    CONSTRAINT uq_question_cluster_members_question
        UNIQUE (question_id)
);

CREATE INDEX idx_question_cluster_members_cluster
    ON question_cluster_members(cluster_id);


CREATE INDEX idx_questions_primary_category
    ON questions(primary_category_id)
    WHERE primary_category_id IS NOT NULL;

CREATE INDEX idx_questions_ai_processing_status
    ON questions(ai_processing_status);