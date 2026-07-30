ALTER TABLE character_management.character_sheet
    ADD COLUMN IF NOT EXISTS derived_statistics TEXT NOT NULL DEFAULT '';

ALTER TABLE character_management.character_sheet_command_history
    ADD COLUMN IF NOT EXISTS derived_statistics TEXT NOT NULL DEFAULT '';
