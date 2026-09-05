ALTER TABLE combat_participant ADD COLUMN IF NOT EXISTS movement_remaining INT NOT NULL DEFAULT 30;
ALTER TABLE combat_participant ADD COLUMN IF NOT EXISTS action_available BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE combat_participant ADD COLUMN IF NOT EXISTS bonus_action_available BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE combat_participant ADD COLUMN IF NOT EXISTS reaction_available BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS combat_action_operation (
    command_id UUID PRIMARY KEY,
    encounter_id UUID NOT NULL REFERENCES combat_encounter(encounter_id) ON DELETE CASCADE,
    actor_id UUID NOT NULL,
    fingerprint TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('RESERVED', 'PROCESSING_FAILED', 'COMMITTED')),
    reserved_movement INT NOT NULL CHECK (reserved_movement >= 0),
    reserved_action BOOLEAN NOT NULL,
    reserved_bonus_action BOOLEAN NOT NULL,
    reserved_reaction BOOLEAN NOT NULL,
    response_version BIGINT,
    response_status TEXT,
    response_dice_total INT,
    response_judgment TEXT,
    response_violations TEXT,
    failure TEXT,
    dice_total INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS combat_action_step (
    command_id UUID NOT NULL REFERENCES combat_action_operation(command_id) ON DELETE CASCADE,
    step_name TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'DONE', 'FAILED')),
    PRIMARY KEY (command_id, step_name),
    UNIQUE (command_id, idempotency_key)
);
