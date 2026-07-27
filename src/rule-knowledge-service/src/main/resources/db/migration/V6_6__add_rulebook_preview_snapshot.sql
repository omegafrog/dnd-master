ALTER TABLE rulebook_registration
    ADD COLUMN IF NOT EXISTS preview_content TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS preview_warnings TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS preview_spans TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS preview_assets TEXT NOT NULL DEFAULT '[]';
