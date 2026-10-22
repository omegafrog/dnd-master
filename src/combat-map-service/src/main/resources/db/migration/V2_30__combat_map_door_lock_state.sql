ALTER TABLE combat_map_door ADD COLUMN locked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE combat_map_door ADD CONSTRAINT combat_map_door_lock_state CHECK (NOT (open AND locked));
ALTER TABLE combat_map_command_door_history ADD COLUMN locked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE combat_map_command_door_history ADD CONSTRAINT combat_map_command_door_lock_state CHECK (NOT (open AND locked));
