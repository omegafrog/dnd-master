ALTER TABLE character_management.character_sheet
    ADD COLUMN IF NOT EXISTS race TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS character_class TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS background TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS starting_abilities TEXT NOT NULL DEFAULT '';

ALTER TABLE character_management.character_sheet_command_history
    ADD COLUMN IF NOT EXISTS race TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS character_class TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS background TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS starting_abilities TEXT NOT NULL DEFAULT '';
