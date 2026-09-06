ALTER TABLE documents RENAME COLUMN thumbnail_url TO thumbnail_key;
ALTER TABLE documents RENAME COLUMN pdf_url TO pdf_key;
ALTER TABLE slides RENAME COLUMN thumbnail_url TO thumbnail_key;
ALTER TABLE document_revisions RENAME COLUMN source_pdf_url TO source_pdf_key;
ALTER TABLE revision_slides RENAME COLUMN thumbnail_url TO thumbnail_key;
ALTER TABLE revision_pages RENAME COLUMN thumbnail_url TO thumbnail_key;
