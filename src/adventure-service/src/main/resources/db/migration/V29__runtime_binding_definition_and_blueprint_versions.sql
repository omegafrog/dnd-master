ALTER TABLE adventure_runtime_binding
    ADD COLUMN IF NOT EXISTS game_system_definition_version BIGINT NOT NULL DEFAULT 0 CHECK (game_system_definition_version >= 0),
    ADD COLUMN IF NOT EXISTS character_blueprint_version BIGINT NOT NULL DEFAULT 0 CHECK (character_blueprint_version >= 0);
