CREATE TABLE IF NOT EXISTS scenario_resolution_override (
    override_id UUID PRIMARY KEY,
    bundle_id UUID NOT NULL REFERENCES scenario_source_bundle(bundle_id) ON DELETE CASCADE,
    owner_player_id UUID NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 1),
    author TEXT NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL,
    anchor_fingerprint TEXT NOT NULL,
    document_fingerprint TEXT NOT NULL,
    content_fingerprint TEXT NOT NULL,
    quote_fingerprint TEXT NOT NULL,
    context_fingerprint TEXT NOT NULL,
    locator_fingerprint TEXT NOT NULL,
    unit_fingerprint TEXT NOT NULL,
    replacement_candidate_json TEXT NOT NULL,
    revision_history TEXT[] NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS scenario_resolution_override_bundle_idx
    ON scenario_resolution_override(bundle_id, anchor_fingerprint);
