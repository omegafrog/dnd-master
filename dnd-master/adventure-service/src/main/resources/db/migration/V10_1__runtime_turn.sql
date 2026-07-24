CREATE TABLE adventure_runtime_turn (
    turn_id UUID PRIMARY KEY,
    adventure_id UUID NOT NULL REFERENCES adventure(adventure_id) ON DELETE CASCADE,
    binding_version BIGINT NOT NULL CHECK (binding_version >= 1),
    scenario_package_id UUID NOT NULL,
    action TEXT NOT NULL,
    runtime_turn_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX adventure_runtime_turn_adventure_idx
    ON adventure_runtime_turn (adventure_id, created_at, turn_id);
