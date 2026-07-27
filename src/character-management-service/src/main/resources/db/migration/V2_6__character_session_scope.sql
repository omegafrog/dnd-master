ALTER TABLE character_management.character_sheet
    ADD COLUMN IF NOT EXISTS session_id UUID;

UPDATE character_management.character_sheet
SET session_id = adventure_id
WHERE session_id IS NULL;

ALTER TABLE character_management.character_sheet
    ALTER COLUMN session_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS character_sheet_session_idx
    ON character_management.character_sheet(session_id);

ALTER TABLE character_management.character_sheet_command_history
    ADD COLUMN IF NOT EXISTS session_id UUID;

UPDATE character_management.character_sheet_command_history
SET session_id = adventure_id
WHERE session_id IS NULL;
