ALTER TABLE report
    ADD COLUMN IF NOT EXISTS page_format integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS page_orientation integer NOT NULL DEFAULT 0;
