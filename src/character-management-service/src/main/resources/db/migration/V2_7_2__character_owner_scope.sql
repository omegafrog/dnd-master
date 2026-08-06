ALTER TABLE character_management.character_sheet
    ADD COLUMN IF NOT EXISTS owner_player_id UUID;

ALTER TABLE character_management.character_sheet_command_history
    ADD COLUMN IF NOT EXISTS owner_player_id UUID;

CREATE INDEX IF NOT EXISTS character_sheet_owner_idx
    ON character_management.character_sheet(owner_player_id);
