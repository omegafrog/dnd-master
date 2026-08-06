ALTER TABLE scenario_package ADD COLUMN IF NOT EXISTS map_definitions_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE scenario_package ADD COLUMN IF NOT EXISTS story_map_bindings_json TEXT NOT NULL DEFAULT '[]';
