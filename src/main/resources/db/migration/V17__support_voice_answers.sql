-- Support text / voice official answers

ALTER TABLE answers
    ADD COLUMN answer_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN transcript TEXT;

ALTER TABLE answers
    ADD CONSTRAINT chk_answers_answer_type
    CHECK (answer_type IN ('TEXT', 'VOICE'));