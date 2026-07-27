ALTER TABLE combat_map
    ADD COLUMN IF NOT EXISTS operation_key TEXT,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

DO $$
BEGIN
    ALTER TABLE combat_map
        ADD CONSTRAINT combat_map_version_non_negative CHECK (version >= 0);
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS combat_map_operation_key_uq
    ON combat_map(operation_key) WHERE operation_key IS NOT NULL;
