CREATE TABLE adventure_stage_backbone (
    adventure_id UUID NOT NULL REFERENCES adventure(adventure_id) ON DELETE CASCADE,
    scenario_package_id UUID NOT NULL REFERENCES scenario_package(package_id) ON DELETE CASCADE,
    backbone_revision BIGINT NOT NULL CHECK (backbone_revision >= 1),
    artifact_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (adventure_id, backbone_revision),
    UNIQUE (adventure_id, scenario_package_id, backbone_revision)
);

CREATE TABLE adventure_detailed_stage (
    adventure_id UUID NOT NULL,
    scenario_package_id UUID NOT NULL REFERENCES scenario_package(package_id) ON DELETE CASCADE,
    backbone_revision BIGINT NOT NULL CHECK (backbone_revision >= 1),
    stage_id TEXT NOT NULL,
    detailed_stage_revision BIGINT NOT NULL CHECK (detailed_stage_revision >= 1),
    artifact_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (adventure_id, backbone_revision, stage_id, detailed_stage_revision),
    FOREIGN KEY (adventure_id, scenario_package_id, backbone_revision)
        REFERENCES adventure_stage_backbone(adventure_id, scenario_package_id, backbone_revision)
        ON DELETE CASCADE
);
