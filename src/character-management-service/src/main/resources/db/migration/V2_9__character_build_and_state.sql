ALTER TABLE character_management.character_sheet
    ADD COLUMN IF NOT EXISTS character_build TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS character_state TEXT NOT NULL DEFAULT '';

ALTER TABLE character_management.character_sheet_command_history
    ADD COLUMN IF NOT EXISTS character_build TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS character_state TEXT NOT NULL DEFAULT '';
