ALTER TABLE documents
    ADD COLUMN category_processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN category_processed_at TIMESTAMPTZ;

ALTER TABLE documents
    ADD CONSTRAINT ck_documents_category_processing_status
    CHECK (
        category_processing_status IN (
            'PENDING',
            'PROCESSING',
            'COMPLETED',
            'FAILED'
        )
    );

CREATE INDEX idx_documents_category_processing_status
    ON documents(category_processing_status);