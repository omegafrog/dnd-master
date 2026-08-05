ALTER TABLE dice_roll ADD COLUMN IF NOT EXISTS operation_key TEXT, ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE UNIQUE INDEX IF NOT EXISTS dice_roll_operation_key_uq ON dice_roll(operation_key) WHERE operation_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS adjudication_delivery_pending_idx ON adjudication_delivery(status, version) WHERE status = 'PENDING';
