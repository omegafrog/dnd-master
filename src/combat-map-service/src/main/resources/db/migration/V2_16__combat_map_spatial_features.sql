ALTER TABLE combat_map ADD COLUMN IF NOT EXISTS spatial_preparation_blocked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE combat_map_command_history ADD COLUMN IF NOT EXISTS spatial_preparation_blocked BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS combat_map_spatial_feature (
    map_id UUID NOT NULL REFERENCES combat_map(map_id) ON DELETE CASCADE,
    feature_id UUID NOT NULL,
    feature_type TEXT NOT NULL CHECK (feature_type IN ('TRAP','SECRET_DOOR','HAZARD_AREA','INTERACTIVE_OBJECT','MAGICAL_AREA_EFFECT')),
    visibility TEXT NOT NULL CHECK (visibility IN ('HIDDEN','DISCOVERED','REVEALED')),
    state TEXT NOT NULL,
    detection_rule_reference TEXT,
    detection_difficulty INTEGER,
    detection_mode TEXT,
    origin TEXT NOT NULL CHECK (origin IN ('STORY_PLAN','GM_RUNTIME','SYSTEM')),
    source_reference TEXT NOT NULL DEFAULT '',
    created_turn BIGINT NOT NULL CHECK (created_turn >= 0),
    created_map_version BIGINT NOT NULL CHECK (created_map_version >= 0),
    repeatable BOOLEAN NOT NULL DEFAULT FALSE,
    remaining_duration_turns INTEGER NOT NULL DEFAULT -1,
    PRIMARY KEY (map_id, feature_id)
);

CREATE TABLE IF NOT EXISTS combat_map_spatial_feature_cell (
    map_id UUID NOT NULL,
    feature_id UUID NOT NULL,
    x INTEGER NOT NULL CHECK (x >= 0),
    y INTEGER NOT NULL CHECK (y >= 0),
    PRIMARY KEY (map_id, feature_id, x, y),
    FOREIGN KEY (map_id, feature_id) REFERENCES combat_map_spatial_feature(map_id, feature_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS combat_map_spatial_feature_trigger (
    map_id UUID NOT NULL,
    feature_id UUID NOT NULL,
    trigger_name TEXT NOT NULL CHECK (trigger_name IN ('ENTER_CELL','LEAVE_CELL','BECOME_VISIBLE','OBSERVE','INTERACT','COMBAT_TURN_START')),
    PRIMARY KEY (map_id, feature_id, trigger_name),
    FOREIGN KEY (map_id, feature_id) REFERENCES combat_map_spatial_feature(map_id, feature_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS combat_map_command_spatial_feature_history (
    command_id UUID NOT NULL REFERENCES combat_map_command_history(command_id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    feature_id UUID NOT NULL,
    feature_type TEXT NOT NULL,
    visibility TEXT NOT NULL,
    state TEXT NOT NULL,
    detection_rule_reference TEXT,
    detection_difficulty INTEGER,
    detection_mode TEXT,
    origin TEXT NOT NULL,
    source_reference TEXT NOT NULL DEFAULT '',
    created_turn BIGINT NOT NULL,
    created_map_version BIGINT NOT NULL,
    repeatable BOOLEAN NOT NULL DEFAULT FALSE,
    remaining_duration_turns INTEGER NOT NULL DEFAULT -1,
    PRIMARY KEY (command_id, feature_id)
);

CREATE TABLE IF NOT EXISTS combat_map_command_spatial_feature_cell_history (
    command_id UUID NOT NULL,
    feature_id UUID NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    PRIMARY KEY (command_id, feature_id, x, y),
    FOREIGN KEY (command_id, feature_id) REFERENCES combat_map_command_spatial_feature_history(command_id, feature_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS combat_map_command_spatial_feature_trigger_history (
    command_id UUID NOT NULL,
    feature_id UUID NOT NULL,
    trigger_name TEXT NOT NULL,
    PRIMARY KEY (command_id, feature_id, trigger_name),
    FOREIGN KEY (command_id, feature_id) REFERENCES combat_map_command_spatial_feature_history(command_id, feature_id) ON DELETE CASCADE
);

-- Existing TRAP/OBJECT rows are display-only legacy state. Reuse token_id as a deterministic feature identity.
INSERT INTO combat_map_spatial_feature(
    map_id, feature_id, feature_type, visibility, state, origin, source_reference,
    created_turn, created_map_version, repeatable, remaining_duration_turns)
SELECT token.map_id, token.token_id,
       CASE token.token_type WHEN 'TRAP' THEN 'TRAP' ELSE 'INTERACTIVE_OBJECT' END,
       CASE token.discovery WHEN 'HIDDEN' THEN 'HIDDEN' ELSE 'REVEALED' END,
       CASE token.discovery WHEN 'HIDDEN' THEN 'HIDDEN' ELSE 'REVEALED' END,
       'SYSTEM', 'legacy-token', 0, map.version, FALSE, -1
FROM combat_map_token token
JOIN combat_map map ON map.map_id = token.map_id
WHERE token.token_type IN ('TRAP', 'OBJECT')
ON CONFLICT (map_id, feature_id) DO NOTHING;

INSERT INTO combat_map_spatial_feature_cell(map_id, feature_id, x, y)
SELECT token.map_id, token.token_id, token.x, token.y
FROM combat_map_token token
WHERE token.token_type IN ('TRAP', 'OBJECT')
ON CONFLICT DO NOTHING;

DELETE FROM combat_map_token WHERE token_type IN ('TRAP', 'OBJECT');
