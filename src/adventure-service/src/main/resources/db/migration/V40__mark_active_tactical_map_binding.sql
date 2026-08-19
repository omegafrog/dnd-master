ALTER TABLE adventure_active_tactical_map
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS adventure_active_tactical_map_one_active_owner
    ON adventure_active_tactical_map(adventure_id, owner_player_id)
    WHERE active;
