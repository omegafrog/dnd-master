ALTER TABLE combat_participant
    ADD COLUMN IF NOT EXISTS movement_speed INT NOT NULL DEFAULT 30 CHECK (movement_speed >= 0);

ALTER TABLE combat_participant
    ADD COLUMN IF NOT EXISTS movement_used INT;

UPDATE combat_participant
SET movement_used = GREATEST(0, movement_speed - movement_remaining)
WHERE movement_used IS NULL;

ALTER TABLE combat_participant
    ALTER COLUMN movement_used SET DEFAULT 0,
    ALTER COLUMN movement_used SET NOT NULL;
