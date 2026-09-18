CREATE TABLE IF NOT EXISTS combat_map_movement_operation (
    operation_id UUID PRIMARY KEY,
    map_id UUID NOT NULL REFERENCES combat_map(map_id) ON DELETE CASCADE,
    command_id UUID NOT NULL UNIQUE,
    player_id UUID NOT NULL,
    token_id UUID NOT NULL,
    requested_path TEXT NOT NULL,
    path_distance INTEGER NOT NULL,
    fingerprint TEXT NOT NULL,
    expected_version BIGINT NOT NULL,
    status TEXT NOT NULL,
    cursor INTEGER NOT NULL,
    current_x INTEGER NOT NULL,
    current_y INTEGER NOT NULL,
    traversed_path TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS combat_map_movement_operation_active_map_uq
    ON combat_map_movement_operation(map_id) WHERE status IN ('PREPARING', 'RETRY_WAIT', 'READY_TO_COMMIT');
