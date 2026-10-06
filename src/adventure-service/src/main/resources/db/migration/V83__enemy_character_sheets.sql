CREATE TABLE IF NOT EXISTS enemy_character_sheet (
    identity_key TEXT PRIMARY KEY,
    adventure_id UUID NOT NULL REFERENCES adventure(adventure_id) ON DELETE CASCADE,
    sheet_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_enemy_character_sheet_adventure
    ON enemy_character_sheet (adventure_id);
