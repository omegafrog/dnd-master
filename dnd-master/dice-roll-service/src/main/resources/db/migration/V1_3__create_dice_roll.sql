CREATE TABLE dice_roll (
    roll_id UUID PRIMARY KEY,
    adventure_id UUID NOT NULL,
    rule_set_id UUID NOT NULL,
    scope TEXT NOT NULL CHECK (scope IN ('PLAYER_ACTION', 'NPC', 'ENEMY', 'SECRET_CHECK')),
    dice_count INTEGER NOT NULL CHECK (dice_count BETWEEN 1 AND 100),
    dice_sides INTEGER NOT NULL CHECK (dice_sides BETWEEN 2 AND 1000),
    modifier INTEGER NOT NULL,
    faces INTEGER[] NOT NULL,
    total INTEGER NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 0)
);

CREATE TABLE adjudication_delivery (
    delivery_key TEXT PRIMARY KEY,
    roll_id UUID NOT NULL REFERENCES dice_roll(roll_id),
    payload_hash TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'FAILED', 'COMPLETED')),
    attempts INTEGER NOT NULL CHECK (attempts > 0),
    version BIGINT NOT NULL CHECK (version >= 0),
    failure_reason TEXT
);
