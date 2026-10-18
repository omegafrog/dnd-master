CREATE TABLE IF NOT EXISTS adventure_ability_definition (
    adventure_id UUID NOT NULL REFERENCES adventure(adventure_id) ON DELETE CASCADE,
    ability_id TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PREPARING', 'ACTIVE')),
    definition_json JSONB NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (adventure_id, ability_id)
);
