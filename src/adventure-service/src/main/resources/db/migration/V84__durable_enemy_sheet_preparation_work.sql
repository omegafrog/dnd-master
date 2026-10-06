ALTER TABLE combat_work_item
    ADD COLUMN enemy_sheet_preparation_request JSONB;

ALTER TABLE combat_work_item
    DROP CONSTRAINT IF EXISTS combat_work_item_work_type_check;

ALTER TABLE combat_work_item
    ADD CONSTRAINT combat_work_item_work_type_check
    CHECK (work_type IN ('AI_TURN', 'ENEMY_SHEET_PREPARATION'));

CREATE UNIQUE INDEX IF NOT EXISTS combat_work_item_enemy_sheet_preparation_uq
    ON combat_work_item(encounter_id)
    WHERE work_type = 'ENEMY_SHEET_PREPARATION' AND status <> 'FAILED';
