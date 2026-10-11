ALTER TABLE scenario_package
    ADD COLUMN IF NOT EXISTS spell_definition_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS spell_definition_locked BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS scenario_package_spell_definition_history (
    package_id UUID NOT NULL REFERENCES scenario_package(package_id) ON DELETE CASCADE,
    revision BIGINT NOT NULL CHECK (revision > 0),
    definition_id TEXT NOT NULL,
    editor_id UUID NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    previous_definition_json JSONB NOT NULL,
    current_definition_json JSONB NOT NULL,
    PRIMARY KEY (package_id, revision)
);
