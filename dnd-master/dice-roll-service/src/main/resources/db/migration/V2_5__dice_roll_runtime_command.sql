ALTER TABLE dice_roll
    ADD COLUMN session_id UUID,
    ADD COLUMN turn_id UUID,
    ADD COLUMN expected_version BIGINT NOT NULL DEFAULT 0;

UPDATE dice_roll
SET expected_version = version
WHERE expected_version = 0;

ALTER TABLE dice_roll
    ALTER COLUMN session_id SET NOT NULL,
    ALTER COLUMN turn_id SET NOT NULL;
