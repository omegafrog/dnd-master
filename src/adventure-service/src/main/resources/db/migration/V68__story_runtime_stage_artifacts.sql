CREATE TABLE IF NOT EXISTS story_stage_backbone (
    scenario_package_id UUID NOT NULL REFERENCES scenario_package(package_id) ON DELETE CASCADE,
    backbone_revision BIGINT NOT NULL CHECK (backbone_revision >= 1),
    artifact_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (scenario_package_id, backbone_revision)
);

CREATE TABLE IF NOT EXISTS story_detailed_stage (
    scenario_package_id UUID NOT NULL REFERENCES scenario_package(package_id) ON DELETE CASCADE,
    backbone_revision BIGINT NOT NULL CHECK (backbone_revision >= 1),
    stage_id TEXT NOT NULL,
    detailed_stage_revision BIGINT NOT NULL CHECK (detailed_stage_revision >= 1),
    artifact_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (scenario_package_id, stage_id, backbone_revision, detailed_stage_revision),
    FOREIGN KEY (scenario_package_id, backbone_revision)
        REFERENCES story_stage_backbone(scenario_package_id, backbone_revision)
        ON DELETE CASCADE
);

ALTER TABLE adventure_runtime_binding
    ADD COLUMN IF NOT EXISTS stage_backbone_revision BIGINT,
    ADD COLUMN IF NOT EXISTS current_stage_id TEXT,
    ADD COLUMN IF NOT EXISTS detailed_stage_revision BIGINT;

ALTER TABLE adventure_runtime_binding
    ADD CONSTRAINT adventure_runtime_binding_stage_revision_check
    CHECK (stage_backbone_revision IS NULL OR stage_backbone_revision >= 1);
ALTER TABLE adventure_runtime_binding
    ADD CONSTRAINT adventure_runtime_binding_detailed_revision_check
    CHECK (detailed_stage_revision IS NULL OR detailed_stage_revision >= 1);
