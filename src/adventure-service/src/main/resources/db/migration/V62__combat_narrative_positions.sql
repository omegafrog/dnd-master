CREATE TABLE IF NOT EXISTS combat_narrative_position (
    encounter_id UUID NOT NULL REFERENCES combat_encounter(encounter_id) ON DELETE CASCADE,
    subject_id UUID NOT NULL,
    target_id UUID NOT NULL,
    range_band TEXT NOT NULL CHECK (length(trim(range_band)) > 0),
    cover TEXT NOT NULL CHECK (length(trim(cover)) > 0),
    PRIMARY KEY (encounter_id, subject_id, target_id)
);
