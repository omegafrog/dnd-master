CREATE TABLE IF NOT EXISTS combat_encounter (
    encounter_id UUID PRIMARY KEY,
    adventure_id UUID NOT NULL REFERENCES adventure(adventure_id) ON DELETE CASCADE,
    status TEXT NOT NULL CHECK (status IN ('PREPARING', 'ACTIVE', 'ENDED')),
    round INT NOT NULL CHECK (round >= 1),
    current_participant_id UUID NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 1),
    event_cursor BIGINT NOT NULL DEFAULT 0 CHECK (event_cursor >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS combat_encounter_active_adventure_uq
    ON combat_encounter(adventure_id) WHERE status IN ('PREPARING', 'ACTIVE');
CREATE TABLE IF NOT EXISTS combat_participant (
    encounter_id UUID NOT NULL REFERENCES combat_encounter(encounter_id) ON DELETE CASCADE,
    participant_id UUID NOT NULL,
    display_name TEXT NOT NULL,
    controller TEXT NOT NULL CHECK (controller IN ('PLAYER', 'AI')),
    initiative INT NOT NULL,
    public_condition TEXT,
    PRIMARY KEY (encounter_id, participant_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS combat_participant_initiative_uq
    ON combat_participant(encounter_id, initiative, participant_id);
CREATE TABLE IF NOT EXISTS combat_event (
    encounter_id UUID NOT NULL REFERENCES combat_encounter(encounter_id) ON DELETE CASCADE,
    sequence BIGINT NOT NULL CHECK (sequence >= 0),
    event_type TEXT NOT NULL,
    player_payload JSONB NOT NULL,
    PRIMARY KEY (encounter_id, sequence)
);
