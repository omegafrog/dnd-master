ALTER TABLE dice_roll
    ADD COLUMN session_id UUID,
    ADD COLUMN turn_id UUID,
    ADD COLUMN expected_version BIGINT NOT NULL DEFAULT 0;

UPDATE dice_roll
SET expected_version = version
WHERE expected_version = 0;

UPDATE dice_roll roll_row
SET session_id = roll_row.roll_id,
    turn_id = roll_row.roll_id,
    operation_key = COALESCE(roll_row.operation_key, roll_row.roll_id::text)
WHERE session_id IS NULL OR turn_id IS NULL OR operation_key IS NULL;

ALTER TABLE dice_roll
    ALTER COLUMN session_id SET NOT NULL,
    ALTER COLUMN turn_id SET NOT NULL;
