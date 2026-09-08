CREATE TABLE IF NOT EXISTS combat_map_alignment (
    map_id UUID PRIMARY KEY REFERENCES combat_map(map_id) ON DELETE CASCADE,
    image_revision TEXT NOT NULL,
    origin_x DOUBLE PRECISION NOT NULL,
    origin_y DOUBLE PRECISION NOT NULL,
    cell_size DOUBLE PRECISION NOT NULL CHECK (cell_size > 0),
    version BIGINT NOT NULL CHECK (version > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS combat_map_alignment_command (
    map_id UUID NOT NULL REFERENCES combat_map(map_id) ON DELETE CASCADE,
    owner_player_id UUID NOT NULL,
    command_id UUID NOT NULL,
    expected_version BIGINT NOT NULL,
    image_revision TEXT NOT NULL,
    origin_x DOUBLE PRECISION NOT NULL,
    origin_y DOUBLE PRECISION NOT NULL,
    cell_size DOUBLE PRECISION NOT NULL,
    result_version BIGINT NOT NULL,
    PRIMARY KEY (map_id, owner_player_id, command_id)
);
