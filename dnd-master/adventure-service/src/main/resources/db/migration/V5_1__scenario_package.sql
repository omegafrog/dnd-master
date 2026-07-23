CREATE TABLE scenario_package (
    package_id UUID PRIMARY KEY,
    bundle_id UUID NOT NULL REFERENCES scenario_source_bundle(bundle_id),
    bundle_revision BIGINT NOT NULL CHECK (bundle_revision >= 1),
    input_fingerprint TEXT NOT NULL UNIQUE,
    report_status TEXT NOT NULL,
    report_warnings TEXT[] NOT NULL DEFAULT '{}',
    published_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE scenario_package_document (
    package_id UUID NOT NULL REFERENCES scenario_package(package_id) ON DELETE CASCADE,
    selection_order INT NOT NULL CHECK (selection_order >= 0),
    knowledge_document_id UUID NOT NULL,
    document_type TEXT NOT NULL,
    original_filename TEXT NOT NULL,
    document_role TEXT NOT NULL,
    knowledge_document_status TEXT NOT NULL,
    extraction_version BIGINT NOT NULL CHECK (extraction_version >= 1),
    PRIMARY KEY (package_id, selection_order),
    UNIQUE (package_id, knowledge_document_id)
);

CREATE TABLE scenario_package_resolution_unit (
    package_id UUID NOT NULL REFERENCES scenario_package(package_id) ON DELETE CASCADE,
    unit_order INT NOT NULL CHECK (unit_order >= 0),
    resolution_kind TEXT,
    ability_or_skill TEXT,
    dc INT,
    dice_expression TEXT,
    visibility TEXT NOT NULL,
    source_quote TEXT NOT NULL,
    provenance TEXT NOT NULL,
    status TEXT NOT NULL,
    validation_messages TEXT[] NOT NULL DEFAULT '{}',
    PRIMARY KEY (package_id, unit_order)
);

CREATE TABLE scenario_package_resolution_source_ref (
    package_id UUID NOT NULL,
    unit_order INT NOT NULL,
    ref_order INT NOT NULL CHECK (ref_order >= 0),
    knowledge_document_id UUID NOT NULL,
    extraction_version BIGINT NOT NULL CHECK (extraction_version >= 1),
    locator TEXT NOT NULL,
    PRIMARY KEY (package_id, unit_order, ref_order),
    FOREIGN KEY (package_id, unit_order)
        REFERENCES scenario_package_resolution_unit(package_id, unit_order)
        ON DELETE CASCADE
);
