ALTER TABLE adventure_gm_turn DROP CONSTRAINT IF EXISTS adventure_gm_turn_status_check;
ALTER TABLE adventure_gm_turn
    ADD CONSTRAINT adventure_gm_turn_status_check
    CHECK (status IN ('STARTED', 'PROCESSING', 'COMMITTED', 'FAILED', 'FAILED_RETRYABLE'));
