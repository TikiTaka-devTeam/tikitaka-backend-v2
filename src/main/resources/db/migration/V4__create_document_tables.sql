CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    thumbnail_url TEXT NOT NULL,
    pdf_url TEXT NOT NULL,
    page_count INTEGER NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_documents_space FOREIGN KEY (space_id) REFERENCES spaces(id) ON DELETE CASCADE,
    CONSTRAINT ck_documents_page_count CHECK (page_count >= 1),
    CONSTRAINT ck_documents_version CHECK (version >= 1)
);

CREATE INDEX idx_documents_space_created_at ON documents(space_id, created_at DESC);

CREATE TABLE slides (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL,
    page_number INTEGER NOT NULL,
    thumbnail_url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_slides_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT uq_slides_document_page UNIQUE (document_id, page_number),
    CONSTRAINT ck_slides_page_number CHECK (page_number >= 1)
);

CREATE INDEX idx_slides_document_id ON slides(document_id);

CREATE TABLE document_revisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL,
    editor_id UUID NOT NULL,
    base_document_version INTEGER NOT NULL,
    preview_version INTEGER NOT NULL DEFAULT 1,
    source_file_name VARCHAR(255),
    source_pdf_url TEXT,
    source_page_count INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'EDITING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT fk_document_revisions_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT fk_document_revisions_editor FOREIGN KEY (editor_id) REFERENCES users(id),
    CONSTRAINT ck_document_revisions_base_version CHECK (base_document_version >= 1),
    CONSTRAINT ck_document_revisions_preview_version CHECK (preview_version >= 1),
    CONSTRAINT ck_document_revisions_source_page_count CHECK (source_page_count IS NULL OR source_page_count >= 1),
    CONSTRAINT ck_document_revisions_status CHECK (status IN ('EDITING', 'PROCESSING', 'COMPLETED', 'CANCELED'))
);

CREATE INDEX idx_document_revisions_document_status ON document_revisions(document_id, status);
CREATE INDEX idx_document_revisions_editor_created_at ON document_revisions(editor_id, created_at DESC);

CREATE TABLE revision_slides (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    revision_id UUID NOT NULL,
    source_page_number INTEGER NOT NULL,
    thumbnail_url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_revision_slides_revision FOREIGN KEY (revision_id) REFERENCES document_revisions(id) ON DELETE CASCADE,
    CONSTRAINT uq_revision_slides_page UNIQUE (revision_id, source_page_number),
    CONSTRAINT ck_revision_slides_page_number CHECK (source_page_number >= 1)
);

CREATE INDEX idx_revision_slides_revision_id ON revision_slides(revision_id);

CREATE TABLE revision_pages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    revision_id UUID NOT NULL,
    position INTEGER NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    original_slide_id UUID,
    revision_slide_id UUID,
    thumbnail_url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_revision_pages_revision FOREIGN KEY (revision_id) REFERENCES document_revisions(id) ON DELETE CASCADE,
    CONSTRAINT fk_revision_pages_original_slide FOREIGN KEY (original_slide_id) REFERENCES slides(id),
    CONSTRAINT fk_revision_pages_revision_slide FOREIGN KEY (revision_slide_id) REFERENCES revision_slides(id),
    CONSTRAINT uq_revision_pages_position UNIQUE (revision_id, position),
    CONSTRAINT ck_revision_pages_position CHECK (position >= 1),
    CONSTRAINT ck_revision_pages_source_type CHECK (source_type IN ('ORIGINAL', 'REVISION')),
    CONSTRAINT ck_revision_pages_source_reference CHECK (
        (source_type = 'ORIGINAL' AND original_slide_id IS NOT NULL AND revision_slide_id IS NULL)
        OR
        (source_type = 'REVISION' AND original_slide_id IS NULL AND revision_slide_id IS NOT NULL)
    )
);

CREATE INDEX idx_revision_pages_revision_id ON revision_pages(revision_id);

CREATE TABLE revision_operations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    revision_id UUID NOT NULL,
    client_operation_id UUID NOT NULL,
    sequence INTEGER NOT NULL,
    type VARCHAR(20) NOT NULL,
    payload JSONB NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'APPLIED',
    preview_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_revision_operations_revision FOREIGN KEY (revision_id) REFERENCES document_revisions(id) ON DELETE CASCADE,
    CONSTRAINT uq_revision_operations_client UNIQUE (revision_id, client_operation_id),
    CONSTRAINT uq_revision_operations_sequence UNIQUE (revision_id, sequence),
    CONSTRAINT ck_revision_operations_sequence CHECK (sequence >= 1),
    CONSTRAINT ck_revision_operations_type CHECK (type IN ('INSERT', 'DELETE')),
    CONSTRAINT ck_revision_operations_state CHECK (state IN ('APPLIED', 'UNDONE')),
    CONSTRAINT ck_revision_operations_preview_version CHECK (preview_version >= 1)
);

CREATE INDEX idx_revision_operations_revision_sequence ON revision_operations(revision_id, sequence DESC);

CREATE TABLE recent_document_views (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    document_id UUID NOT NULL,
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_recent_document_views_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_recent_document_views_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT uq_recent_document_views UNIQUE (user_id, document_id)
);

CREATE INDEX idx_recent_document_views_user_time ON recent_document_views(user_id, viewed_at DESC);
