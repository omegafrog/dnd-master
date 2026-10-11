CREATE TABLE IF NOT EXISTS spell_definition_review_preparation (
    preparation_id UUID PRIMARY KEY,
    package_id UUID NOT NULL REFERENCES scenario_package(package_id) ON DELETE CASCADE,
    owner_player_id UUID NOT NULL,
    request_key TEXT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    definitions_json JSONB NOT NULL,
    session_id UUID UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(owner_player_id, request_key)
);

CREATE TABLE IF NOT EXISTS spell_definition_review_preparation_history (
    preparation_id UUID NOT NULL REFERENCES spell_definition_review_preparation(preparation_id) ON DELETE CASCADE,
    revision BIGINT NOT NULL CHECK (revision > 0),
    definition_id TEXT NOT NULL,
    editor_id UUID NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    previous_definition_json JSONB NOT NULL,
    current_definition_json JSONB NOT NULL,
    PRIMARY KEY (preparation_id, revision)
);

DROP TABLE IF EXISTS scenario_package_spell_definition_history;
ALTER TABLE scenario_package DROP COLUMN IF EXISTS spell_definition_revision;
ALTER TABLE scenario_package DROP COLUMN IF EXISTS spell_definition_locked;
