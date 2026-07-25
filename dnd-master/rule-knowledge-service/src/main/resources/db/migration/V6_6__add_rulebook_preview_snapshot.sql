ALTER TABLE rulebook_registration
    ADD COLUMN preview_content TEXT NOT NULL DEFAULT '',
    ADD COLUMN preview_warnings TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN preview_spans TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN preview_assets TEXT NOT NULL DEFAULT '[]';
