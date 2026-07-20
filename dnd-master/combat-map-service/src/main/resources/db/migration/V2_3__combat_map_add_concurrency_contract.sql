ALTER TABLE combat_map
    ADD COLUMN operation_key TEXT,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD CONSTRAINT combat_map_version_non_negative CHECK (version >= 0);

CREATE UNIQUE INDEX combat_map_operation_key_uq
    ON combat_map(operation_key) WHERE operation_key IS NOT NULL;
