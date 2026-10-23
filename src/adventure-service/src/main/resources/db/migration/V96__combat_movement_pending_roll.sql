ALTER TABLE combat_action_operation ADD COLUMN IF NOT EXISTS movement_follow_up TEXT;
ALTER TABLE combat_action_operation ADD COLUMN IF NOT EXISTS response_narration TEXT;
ALTER TABLE combat_action_operation DROP CONSTRAINT IF EXISTS combat_action_operation_status_check;
ALTER TABLE combat_action_operation ADD CONSTRAINT combat_action_operation_status_check
    CHECK (status IN ('RESERVED', 'GM_INTERVENTION_PENDING', 'PROCESSING_FAILED', 'COMMITTED'));
