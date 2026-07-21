ALTER TABLE report
    ADD COLUMN IF NOT EXISTS template_type integer NOT NULL DEFAULT 0;
