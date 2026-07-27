ALTER TABLE character_management.character_sheet
    ADD COLUMN IF NOT EXISTS operation_key TEXT,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS character_sheet_operation_key_uq
    ON character_management.character_sheet(operation_key) WHERE operation_key IS NOT NULL;
