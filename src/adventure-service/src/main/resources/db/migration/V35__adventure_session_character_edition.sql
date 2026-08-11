ALTER TABLE adventure_session
    ADD COLUMN IF NOT EXISTS character_edition TEXT NOT NULL DEFAULT 'DND_5E_2014';
