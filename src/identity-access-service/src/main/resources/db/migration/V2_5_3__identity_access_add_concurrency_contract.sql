ALTER TABLE identity_access.players ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0), ADD COLUMN IF NOT EXISTS operation_key TEXT;
ALTER TABLE identity_access.login_sessions ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
CREATE UNIQUE INDEX IF NOT EXISTS players_operation_key_uq ON identity_access.players(operation_key) WHERE operation_key IS NOT NULL;
