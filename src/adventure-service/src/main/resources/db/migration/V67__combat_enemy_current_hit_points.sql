ALTER TABLE combat_participant
    ADD COLUMN IF NOT EXISTS current_hit_points INT;

UPDATE combat_participant
SET current_hit_points = (stat_block_json ->> 'hitPointMaximum')::INT
WHERE current_hit_points IS NULL
  AND stat_block_json IS NOT NULL
  AND stat_block_json ? 'hitPointMaximum';

ALTER TABLE combat_participant
    DROP CONSTRAINT IF EXISTS combat_participant_current_hit_points_check;

ALTER TABLE combat_participant
    ADD CONSTRAINT combat_participant_current_hit_points_check
    CHECK (current_hit_points IS NULL OR current_hit_points >= 0);
