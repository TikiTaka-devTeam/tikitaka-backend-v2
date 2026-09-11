ALTER TABLE slides
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE slides
    ADD CONSTRAINT ck_slides_status
        CHECK (status IN ('ACTIVE', 'PLACEHOLDER'));

ALTER TABLE document_revisions
    ALTER COLUMN preview_version SET DEFAULT 0;

ALTER TABLE document_revisions
    DROP CONSTRAINT ck_document_revisions_preview_version;

ALTER TABLE document_revisions
    ADD CONSTRAINT ck_document_revisions_preview_version
        CHECK (preview_version >= 0);

ALTER TABLE document_revisions
    ADD COLUMN operation_cursor_sequence INTEGER;

CREATE UNIQUE INDEX uq_document_revisions_active_document
    ON document_revisions(document_id)
    WHERE status IN ('EDITING', 'PROCESSING');

ALTER TABLE revision_pages
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE revision_pages
    ADD CONSTRAINT ck_revision_pages_status
        CHECK (status IN ('ACTIVE', 'DELETE_PENDING'));

ALTER TABLE revision_operations
    ADD COLUMN inverse_payload JSONB;

UPDATE revision_operations
SET inverse_payload = '{}'::jsonb
WHERE inverse_payload IS NULL;

ALTER TABLE revision_operations
    ALTER COLUMN inverse_payload SET NOT NULL;

ALTER TABLE revision_operations
    DROP CONSTRAINT ck_revision_operations_state;

ALTER TABLE revision_operations
    ADD CONSTRAINT ck_revision_operations_state
        CHECK (state IN ('APPLIED', 'UNDONE', 'DISCARDED'));
