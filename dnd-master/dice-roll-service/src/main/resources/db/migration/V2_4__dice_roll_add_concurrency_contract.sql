ALTER TABLE dice_roll
    ADD COLUMN operation_key TEXT,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE UNIQUE INDEX dice_roll_operation_key_uq
    ON dice_roll(operation_key) WHERE operation_key IS NOT NULL;

CREATE INDEX adjudication_delivery_pending_idx
    ON adjudication_delivery(status, version) WHERE status = 'PENDING';
