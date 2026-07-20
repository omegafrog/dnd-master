ALTER TABLE adventure
    ADD COLUMN operation_key TEXT,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE UNIQUE INDEX adventure_operation_key_uq
    ON adventure(operation_key) WHERE operation_key IS NOT NULL;
