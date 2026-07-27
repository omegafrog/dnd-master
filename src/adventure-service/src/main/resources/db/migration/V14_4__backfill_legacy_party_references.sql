DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'adventure' AND column_name = 'character_sheet_id') THEN
        UPDATE adventure
        SET party_json = json_build_array(json_build_object(
            'characterSheetId', character_sheet_id, 'controlMode', 'DIRECT',
            'nameMutableAfterStart', false, 'raceMutableAfterStart', false,
            'characterClassMutableAfterStart', false, 'backgroundMutableAfterStart', false,
            'startingAbilitiesMutableAfterStart', false, 'levelMutableAfterStart', false))::text
        WHERE (party_json IS NULL OR party_json = '[]') AND character_sheet_id IS NOT NULL;
        ALTER TABLE adventure ALTER COLUMN character_sheet_id DROP NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'adventure_runtime_binding' AND column_name = 'character_sheet_id') THEN
        UPDATE adventure_runtime_binding
        SET party_json = json_build_array(json_build_object(
            'characterSheetId', character_sheet_id, 'controlMode', 'DIRECT',
            'nameMutableAfterStart', false, 'raceMutableAfterStart', false,
            'characterClassMutableAfterStart', false, 'backgroundMutableAfterStart', false,
            'startingAbilitiesMutableAfterStart', false, 'levelMutableAfterStart', false))::text
        WHERE (party_json IS NULL OR party_json = '[]') AND character_sheet_id IS NOT NULL;
        ALTER TABLE adventure_runtime_binding ALTER COLUMN character_sheet_id DROP NOT NULL;
    END IF;
END $$;
