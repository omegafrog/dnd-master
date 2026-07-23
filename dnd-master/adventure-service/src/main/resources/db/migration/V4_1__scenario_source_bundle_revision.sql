CREATE TABLE scenario_source_bundle (
    bundle_id UUID PRIMARY KEY,
    owner_player_id UUID NOT NULL,
    current_revision BIGINT NOT NULL CHECK (current_revision >= 1)
);

CREATE INDEX scenario_source_bundle_owner_idx
    ON scenario_source_bundle(owner_player_id);

CREATE TABLE scenario_source_bundle_revision (
    bundle_id UUID NOT NULL REFERENCES scenario_source_bundle(bundle_id) ON DELETE CASCADE,
    revision_number BIGINT NOT NULL CHECK (revision_number >= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (bundle_id, revision_number)
);

CREATE TABLE scenario_source_bundle_revision_document (
    bundle_id UUID NOT NULL,
    revision_number BIGINT NOT NULL,
    selection_order INT NOT NULL CHECK (selection_order >= 0),
    knowledge_document_id UUID NOT NULL,
    document_type TEXT NOT NULL,
    original_filename TEXT NOT NULL,
    document_role TEXT NOT NULL,
    knowledge_document_status TEXT NOT NULL,
    extraction_version BIGINT NOT NULL CHECK (extraction_version >= 1),
    PRIMARY KEY (bundle_id, revision_number, selection_order),
    UNIQUE (bundle_id, revision_number, knowledge_document_id),
    FOREIGN KEY (bundle_id, revision_number)
        REFERENCES scenario_source_bundle_revision(bundle_id, revision_number)
        ON DELETE CASCADE
);
