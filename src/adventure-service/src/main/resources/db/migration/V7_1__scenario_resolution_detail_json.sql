ALTER TABLE scenario_package_resolution_unit
    ADD COLUMN IF NOT EXISTS detail_json TEXT NOT NULL DEFAULT '{}';
