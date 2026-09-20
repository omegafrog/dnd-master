ALTER TABLE combat_map_spatial_feature ADD COLUMN IF NOT EXISTS removal_policy TEXT NOT NULL DEFAULT '';
ALTER TABLE combat_map_spatial_feature ADD COLUMN IF NOT EXISTS overlap_allowed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE combat_map_command_spatial_feature_history ADD COLUMN IF NOT EXISTS removal_policy TEXT NOT NULL DEFAULT '';
ALTER TABLE combat_map_command_spatial_feature_history ADD COLUMN IF NOT EXISTS overlap_allowed BOOLEAN NOT NULL DEFAULT FALSE;
