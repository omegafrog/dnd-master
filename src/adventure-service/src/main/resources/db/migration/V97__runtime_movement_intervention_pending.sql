ALTER TABLE adventure_runtime_turn_command DROP CONSTRAINT IF EXISTS adventure_runtime_turn_command_execution_status_check;
ALTER TABLE adventure_runtime_turn_command ADD CONSTRAINT adventure_runtime_turn_command_execution_status_check
    CHECK (execution_status IN ('PENDING', 'GM_INTERVENTION_PENDING', 'DONE', 'FAILED'));
