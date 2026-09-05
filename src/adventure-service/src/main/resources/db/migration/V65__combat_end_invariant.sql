ALTER TABLE combat_encounter DROP CONSTRAINT IF EXISTS combat_encounter_status_check;
ALTER TABLE combat_encounter ADD CONSTRAINT combat_encounter_status_check
    CHECK (status IN ('PREPARING', 'ACTIVE', 'REACTION_PENDING', 'ENDED'));
DROP INDEX IF EXISTS combat_encounter_active_adventure_uq;
CREATE UNIQUE INDEX IF NOT EXISTS combat_encounter_active_adventure_uq
    ON combat_encounter(adventure_id) WHERE status IN ('PREPARING', 'ACTIVE', 'REACTION_PENDING');
