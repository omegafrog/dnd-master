ALTER TABLE combat_map_token
    ADD COLUMN IF NOT EXISTS elevation INTEGER NOT NULL DEFAULT 0 CHECK (elevation >= 0);

ALTER TABLE combat_map_command_token_history
    ADD COLUMN IF NOT EXISTS elevation INTEGER NOT NULL DEFAULT 0 CHECK (elevation >= 0);
