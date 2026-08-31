CREATE TABLE IF NOT EXISTS combat_map_command_history (
    command_id UUID PRIMARY KEY, map_id UUID NOT NULL REFERENCES combat_map(map_id) ON DELETE CASCADE,
    owner_player_id UUID NOT NULL, adventure_id UUID NOT NULL, rule_set_id UUID NOT NULL,
    grid_width INTEGER NOT NULL, grid_height INTEGER NOT NULL, cell_size INTEGER NOT NULL, distance_unit INTEGER NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 0), operation_key UUID NOT NULL, operation_fingerprint TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS combat_map_command_token_history (
    command_id UUID NOT NULL REFERENCES combat_map_command_history(command_id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL, token_id UUID NOT NULL, token_type TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL,
    controller TEXT NOT NULL, owner_player_id UUID, PRIMARY KEY (command_id, sequence));
CREATE TABLE IF NOT EXISTS combat_map_command_obstacle_history (
    command_id UUID NOT NULL REFERENCES combat_map_command_history(command_id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, PRIMARY KEY (command_id, sequence));
CREATE TABLE IF NOT EXISTS combat_map_command_layer_history (
    command_id UUID NOT NULL REFERENCES combat_map_command_history(command_id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL, layer_type TEXT NOT NULL, layer_value TEXT NOT NULL, visibility TEXT NOT NULL,
    PRIMARY KEY (command_id, sequence));
CREATE INDEX IF NOT EXISTS combat_map_command_history_map_idx ON combat_map_command_history (map_id, created_at, command_id);
