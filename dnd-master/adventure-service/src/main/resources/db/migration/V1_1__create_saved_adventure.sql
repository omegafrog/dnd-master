CREATE TABLE adventure (
    adventure_id UUID PRIMARY KEY,
    session_id UUID NOT NULL UNIQUE,
    owner_player_id UUID NOT NULL,
    scenario_id UUID NOT NULL,
    rule_set_id UUID NOT NULL,
    character_sheet_id UUID NOT NULL,
    current_scene TEXT NOT NULL,
    npc_state TEXT,
    pending_action TEXT,
    latest_judgment TEXT,
    status TEXT NOT NULL CHECK (status IN ('SAVED', 'DELETED')),
    version BIGINT NOT NULL CHECK (version >= 0)
);

CREATE INDEX adventure_saved_owner_idx ON adventure(owner_player_id) WHERE status = 'SAVED';

CREATE TABLE adventure_conversation (
    adventure_id UUID NOT NULL REFERENCES adventure(adventure_id) ON DELETE CASCADE,
    sequence BIGINT NOT NULL CHECK (sequence >= 0),
    speaker TEXT NOT NULL,
    content TEXT NOT NULL,
    PRIMARY KEY (adventure_id, sequence)
);
