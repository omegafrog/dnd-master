ALTER TABLE adventure_runtime_turn
    ADD COLUMN IF NOT EXISTS command_id UUID,
    ADD COLUMN IF NOT EXISTS session_id UUID;

UPDATE adventure_runtime_turn turn_row
SET session_id = adventure.session_id,
    command_id = turn_row.turn_id
FROM adventure
WHERE adventure.adventure_id = turn_row.adventure_id;

ALTER TABLE adventure_runtime_turn
    ALTER COLUMN command_id SET NOT NULL,
    ALTER COLUMN session_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS adventure_runtime_turn_command_uq
    ON adventure_runtime_turn (command_id);
