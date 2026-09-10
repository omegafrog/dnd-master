CREATE TABLE IF NOT EXISTS combat_map_public_image_artifact (
    owner_player_id UUID NOT NULL,
    map_id UUID NOT NULL REFERENCES combat_map(map_id) ON DELETE CASCADE,
    image_revision TEXT NOT NULL,
    public_area_revision BIGINT NOT NULL CHECK (public_area_revision > 0),
    observation_version BIGINT NOT NULL CHECK (observation_version >= 0),
    png BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (owner_player_id, map_id, image_revision, public_area_revision),
    UNIQUE (owner_player_id, map_id, image_revision, observation_version)
);
