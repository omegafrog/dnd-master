CREATE TABLE IF NOT EXISTS adventure_runtime_binding (
    adventure_id UUID NOT NULL REFERENCES adventure(adventure_id) ON DELETE CASCADE,
    binding_version BIGINT NOT NULL CHECK (binding_version >= 1),
    owner_player_id UUID NOT NULL,
    scenario_package_id UUID NOT NULL,
    scenario_package_revision BIGINT NOT NULL CHECK (scenario_package_revision >= 1),
    rulebook_ids_json TEXT NOT NULL,
    character_sheet_id UUID NOT NULL,
    engine_id TEXT NOT NULL,
    tool_ids_json TEXT NOT NULL,
    playability_status TEXT NOT NULL CHECK (playability_status IN ('PLAYABLE', 'PLAYABLE_WITH_LIMITS', 'BLOCKED')),
    playability_warnings_json TEXT NOT NULL,
    playability_blockers_json TEXT NOT NULL,
    playability_limits_json TEXT NOT NULL,
    active_source_context_json TEXT,
    source_context_candidates_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (adventure_id, binding_version)
);

CREATE INDEX IF NOT EXISTS adventure_runtime_binding_current_idx
    ON adventure_runtime_binding (adventure_id, binding_version DESC);
