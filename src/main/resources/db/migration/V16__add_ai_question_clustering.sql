-- AI question classification / category mapping / embedding support

ALTER TABLE question_categories
    ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN source_pages JSONB;

ALTER TABLE question_categories
    ADD CONSTRAINT ck_question_categories_source_type
    CHECK (source_type IN ('MANUAL', 'AI'));


ALTER TABLE questions
    ADD COLUMN question_scope VARCHAR(20),
    ADD COLUMN embedding VECTOR(768),
    ADD COLUMN ai_processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN ai_processed_at TIMESTAMPTZ;

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


CREATE INDEX idx_questions_ai_processing_status
    ON questions(ai_processing_status);

CREATE INDEX idx_questions_document_scope
    ON questions(document_id, question_scope)
    WHERE question_scope = 'COURSE_RELATED';