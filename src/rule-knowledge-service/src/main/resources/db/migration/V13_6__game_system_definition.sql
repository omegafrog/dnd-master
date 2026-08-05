CREATE TABLE IF NOT EXISTS game_system_definition_revision (
    definition_id UUID PRIMARY KEY,
    rulebook_id UUID NOT NULL REFERENCES rulebook_registration(rulebook_id) ON DELETE CASCADE,
    definition_version BIGINT NOT NULL CHECK (definition_version > 0),
    status TEXT NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED')),
    definition_json TEXT NOT NULL,
    published_at TIMESTAMPTZ,
    UNIQUE (rulebook_id, definition_version)
);
