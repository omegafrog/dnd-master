ALTER TABLE combat_map_token ADD COLUMN IF NOT EXISTS discovery TEXT NOT NULL DEFAULT 'DISCOVERED';
ALTER TABLE combat_map_command_token_history ADD COLUMN IF NOT EXISTS discovery TEXT NOT NULL DEFAULT 'DISCOVERED';
ALTER TABLE combat_map_command_history ADD COLUMN IF NOT EXISTS visibility_current TEXT NOT NULL DEFAULT '';
ALTER TABLE combat_map_command_history ADD COLUMN IF NOT EXISTS visibility_explored TEXT NOT NULL DEFAULT '';
ALTER TABLE combat_map_command_history ADD COLUMN IF NOT EXISTS visibility_last_seen TEXT NOT NULL DEFAULT '';
ALTER TABLE combat_map_command_history ADD COLUMN IF NOT EXISTS visibility_rule_turn BIGINT NOT NULL DEFAULT 0;
CREATE TABLE IF NOT EXISTS combat_map_door(map_id UUID REFERENCES combat_map ON DELETE CASCADE, x INT NOT NULL, y INT NOT NULL, open BOOLEAN NOT NULL, PRIMARY KEY(map_id,x,y));
CREATE TABLE IF NOT EXISTS combat_map_command_door_history(command_id UUID REFERENCES combat_map_command_history ON DELETE CASCADE, sequence INT NOT NULL, x INT NOT NULL, y INT NOT NULL, open BOOLEAN NOT NULL, PRIMARY KEY(command_id,sequence));
