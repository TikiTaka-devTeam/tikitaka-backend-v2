ALTER TABLE document_revisions
    DROP CONSTRAINT ck_document_revisions_status;

ALTER TABLE document_revisions
    ADD CONSTRAINT ck_document_revisions_status
        CHECK (status IN ('EDITING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELED'));
