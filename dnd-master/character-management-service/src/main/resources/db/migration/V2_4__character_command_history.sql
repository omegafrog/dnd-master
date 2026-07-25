CREATE TABLE IF NOT EXISTS character_management.character_sheet_command_history (
    command_id UUID PRIMARY KEY,
    character_sheet_id UUID NOT NULL REFERENCES character_management.character_sheet(character_sheet_id) ON DELETE CASCADE,
    adventure_id UUID NOT NULL,
    edition TEXT NOT NULL,
    character_name TEXT NOT NULL,
    character_level INTEGER NOT NULL,
    inspiration BOOLEAN NOT NULL,
    operation_key UUID NOT NULL,
    operation_fingerprint TEXT NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS character_sheet_command_history_sheet_idx
    ON character_management.character_sheet_command_history (character_sheet_id, created_at, command_id);
