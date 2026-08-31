CREATE TABLE IF NOT EXISTS adventure_active_tactical_map (
    adventure_id UUID NOT NULL,
    stage_position INTEGER NOT NULL CHECK (stage_position > 0),
    owner_player_id UUID NOT NULL,
    combat_map_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (adventure_id, stage_position, owner_player_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS adventure_active_tactical_map_one_active_owner
    ON adventure_active_tactical_map(adventure_id, owner_player_id)
    WHERE active;
