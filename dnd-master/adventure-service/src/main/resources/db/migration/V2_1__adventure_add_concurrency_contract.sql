ALTER TABLE adventure
    ADD COLUMN IF NOT EXISTS operation_key TEXT,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS adventure_operation_key_uq
    ON adventure(operation_key) WHERE operation_key IS NOT NULL;
