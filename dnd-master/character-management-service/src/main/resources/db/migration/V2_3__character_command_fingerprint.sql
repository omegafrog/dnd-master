ALTER TABLE character_management.character_sheet
    ADD COLUMN IF NOT EXISTS operation_fingerprint TEXT;
