CREATE TABLE assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id UUID NOT NULL,
    author_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    auto_close BOOLEAN NOT NULL DEFAULT TRUE,
    closed_at TIMESTAMPTZ,
    max_score NUMERIC(6,2) NOT NULL DEFAULT 100,
    grading_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    finalized_at TIMESTAMPTZ,
    view_count INTEGER NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_assignments_space FOREIGN KEY (space_id) REFERENCES spaces(id) ON DELETE CASCADE,
    CONSTRAINT fk_assignments_author FOREIGN KEY (author_id) REFERENCES users(id),
    CONSTRAINT ck_assignments_max_score CHECK (max_score >= 0),
    CONSTRAINT ck_assignments_grading_status CHECK (grading_status IN ('DRAFT', 'FINALIZED')),
    CONSTRAINT ck_assignments_view_count CHECK (view_count >= 0)
);

CREATE INDEX idx_assignments_space_due_at ON assignments(space_id, due_at DESC) WHERE is_deleted = FALSE;

CREATE TABLE assignment_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_assignment_files_assignment FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE
);

CREATE INDEX idx_assignment_files_assignment_id ON assignment_files(assignment_id);

CREATE TABLE assignment_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID NOT NULL,
    student_id UUID NOT NULL,
    comment TEXT,
    version INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_assignment_submissions_assignment FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_submissions_student FOREIGN KEY (student_id) REFERENCES users(id),
    CONSTRAINT uq_assignment_submissions UNIQUE (assignment_id, student_id),
    CONSTRAINT ck_assignment_submissions_version CHECK (version >= 1),
    CONSTRAINT ck_assignment_submissions_status CHECK (status IN ('SUBMITTED', 'LATE'))
);

CREATE INDEX idx_assignment_submissions_assignment_status ON assignment_submissions(assignment_id, status);
CREATE INDEX idx_assignment_submissions_student_time ON assignment_submissions(student_id, submitted_at DESC);

CREATE TABLE submission_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_submission_files_submission FOREIGN KEY (submission_id) REFERENCES assignment_submissions(id) ON DELETE CASCADE
);

CREATE INDEX idx_submission_files_submission_id ON submission_files(submission_id);

CREATE TABLE assignment_grades (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID NOT NULL,
    student_id UUID NOT NULL,
    score NUMERIC(6,2),
    graded_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_assignment_grades_assignment FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_grades_student FOREIGN KEY (student_id) REFERENCES users(id),
    CONSTRAINT fk_assignment_grades_graded_by FOREIGN KEY (graded_by) REFERENCES users(id),
    CONSTRAINT uq_assignment_grades UNIQUE (assignment_id, student_id),
    CONSTRAINT ck_assignment_grades_score CHECK (score IS NULL OR score >= 0)
);

CREATE INDEX idx_assignment_grades_assignment_id ON assignment_grades(assignment_id);
