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

INSERT INTO identity_access.players (
    player_id,
    username,
    password_hash,
    active,
    created_at
)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'demo-player@example.com',
    '$2y$12$EKoXIc1B7wyIhx3VmCW1c.Yunkh7ZjbHRiE0U2.qvAtzMKBOXrMF6',
    TRUE,
    CURRENT_TIMESTAMP
)
ON CONFLICT (username) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    active = TRUE;
