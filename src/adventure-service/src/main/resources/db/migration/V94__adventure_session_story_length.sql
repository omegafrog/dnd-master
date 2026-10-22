ALTER TABLE adventure_session
    ADD COLUMN story_length TEXT NOT NULL DEFAULT 'NORMAL'
    CHECK (story_length IN ('SHORT', 'NORMAL', 'LONG'));
