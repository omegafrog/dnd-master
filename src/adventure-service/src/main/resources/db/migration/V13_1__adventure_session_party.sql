CREATE TABLE IF NOT EXISTS adventure_session (
    session_id UUID PRIMARY KEY,
    owner_player_id UUID NOT NULL,
    scenario_package_id UUID NOT NULL,
    scenario_package_revision BIGINT NOT NULL CHECK (scenario_package_revision > 0),
    character_limit INTEGER NOT NULL CHECK (character_limit > 0),
    version BIGINT NOT NULL CHECK (version >= 0)
);

CREATE TABLE IF NOT EXISTS adventure_session_party_member (
    session_id UUID NOT NULL REFERENCES adventure_session(session_id) ON DELETE CASCADE,
    character_sheet_id UUID NOT NULL,
    control_mode TEXT NOT NULL CHECK (control_mode IN ('DIRECT', 'AGENT')),
    name_mutable_after_start BOOLEAN NOT NULL,
    race_mutable_after_start BOOLEAN NOT NULL,
    class_mutable_after_start BOOLEAN NOT NULL,
    background_mutable_after_start BOOLEAN NOT NULL,
    abilities_mutable_after_start BOOLEAN NOT NULL,
    level_mutable_after_start BOOLEAN NOT NULL,
    PRIMARY KEY (session_id, character_sheet_id)
);
