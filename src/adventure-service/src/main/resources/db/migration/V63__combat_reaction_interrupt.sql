ALTER TABLE combat_encounter DROP CONSTRAINT IF EXISTS combat_encounter_status_check;
ALTER TABLE combat_encounter ADD CONSTRAINT combat_encounter_status_check
    CHECK (status IN ('PREPARING', 'ACTIVE', 'REACTION_PENDING', 'ENDED'));

ALTER TABLE combat_encounter ADD COLUMN IF NOT EXISTS pending_reaction_id UUID;
ALTER TABLE combat_encounter ADD COLUMN IF NOT EXISTS pending_reaction_trigger TEXT;
ALTER TABLE combat_encounter ADD COLUMN IF NOT EXISTS pending_reaction_actor_id UUID;
ALTER TABLE combat_encounter ADD COLUMN IF NOT EXISTS pending_reaction_operation_id UUID;
ALTER TABLE combat_encounter ADD COLUMN IF NOT EXISTS pending_reaction_resume_step TEXT;
ALTER TABLE combat_encounter ADD COLUMN IF NOT EXISTS pending_reaction_options JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE combat_encounter ADD CONSTRAINT combat_pending_reaction_fields_check CHECK (
    (status = 'REACTION_PENDING' AND pending_reaction_id IS NOT NULL AND pending_reaction_trigger IS NOT NULL
        AND pending_reaction_actor_id IS NOT NULL AND pending_reaction_operation_id IS NOT NULL
        AND pending_reaction_resume_step IS NOT NULL)
    OR (status <> 'REACTION_PENDING' AND pending_reaction_id IS NULL AND pending_reaction_trigger IS NULL
        AND pending_reaction_actor_id IS NULL AND pending_reaction_operation_id IS NULL
        AND pending_reaction_resume_step IS NULL)
);
