CREATE TABLE IF NOT EXISTS scenario_package_blueprint_revision (
    package_id UUID NOT NULL REFERENCES scenario_package(package_id) ON DELETE CASCADE,
    blueprint_revision BIGINT NOT NULL CHECK (blueprint_revision >= 1),
    status TEXT NOT NULL,
    blueprint_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (package_id, blueprint_revision)
);
