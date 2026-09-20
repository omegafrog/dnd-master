CREATE TABLE IF NOT EXISTS adventure_pending_map_movement_confirmation (
    adventure_id UUID PRIMARY KEY,
    owner_player_id UUID NOT NULL,
    map_id UUID NOT NULL,
    token_id UUID NOT NULL,
    map_version BIGINT NOT NULL CHECK (map_version >= 0),
    path_json JSONB NOT NULL,
    distance INTEGER NOT NULL CHECK (distance >= 0),
    fingerprint TEXT NOT NULL CHECK (length(trim(fingerprint)) > 0),
    waypoints_json JSONB NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS adventure_pending_map_movement_confirmation_owner
    ON adventure_pending_map_movement_confirmation(owner_player_id, updated_at DESC);
