ALTER TABLE adventure_runtime_turn
    ADD COLUMN command_id UUID NOT NULL,
    ADD COLUMN session_id UUID NOT NULL;

CREATE UNIQUE INDEX adventure_runtime_turn_command_uq
    ON adventure_runtime_turn (command_id);
