CREATE SCHEMA IF NOT EXISTS identity_access;

CREATE TABLE IF NOT EXISTS identity_access.players (
    player_id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS identity_access.login_sessions (
    session_token UUID PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES identity_access.players(player_id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    logged_out_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS login_sessions_active_player_idx
    ON identity_access.login_sessions (player_id, active);
