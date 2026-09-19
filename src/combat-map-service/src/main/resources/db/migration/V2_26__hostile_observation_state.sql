ALTER TABLE combat_map_token
    ADD COLUMN IF NOT EXISTS hostile_rule_reference TEXT,
    ADD COLUMN IF NOT EXISTS hostile_dice_expression TEXT,
    ADD COLUMN IF NOT EXISTS hostile_modifier INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS hostile_difficulty INTEGER,
    ADD COLUMN IF NOT EXISTS hostile_mode TEXT;

ALTER TABLE combat_map_command_token_history
    ADD COLUMN IF NOT EXISTS hostile_rule_reference TEXT,
    ADD COLUMN IF NOT EXISTS hostile_dice_expression TEXT,
    ADD COLUMN IF NOT EXISTS hostile_modifier INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS hostile_difficulty INTEGER,
    ADD COLUMN IF NOT EXISTS hostile_mode TEXT;

CREATE TABLE IF NOT EXISTS combat_map_hostile_observation (
    map_id UUID NOT NULL REFERENCES combat_map(map_id) ON DELETE CASCADE,
    hostile_token_id UUID NOT NULL,
    player_token_id UUID NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('LOST', 'AWARE')),
    PRIMARY KEY (map_id, hostile_token_id, player_token_id)
);

CREATE TABLE IF NOT EXISTS combat_map_command_hostile_observation_history (
    command_id UUID NOT NULL REFERENCES combat_map_command_history(command_id) ON DELETE CASCADE,
    hostile_token_id UUID NOT NULL,
    player_token_id UUID NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('LOST', 'AWARE')),
    PRIMARY KEY (command_id, hostile_token_id, player_token_id)
);
