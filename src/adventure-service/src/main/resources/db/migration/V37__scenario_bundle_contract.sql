ALTER TABLE scenario_source_bundle ADD COLUMN IF NOT EXISTS name TEXT;
ALTER TABLE scenario_source_bundle ADD COLUMN IF NOT EXISTS rulebook_edition TEXT;
UPDATE scenario_source_bundle SET name = 'Unnamed adventure' WHERE name IS NULL OR btrim(name) = '';
UPDATE scenario_source_bundle SET rulebook_edition = 'DND_5E_2014' WHERE rulebook_edition IS NULL OR btrim(rulebook_edition) = '';
ALTER TABLE scenario_source_bundle ALTER COLUMN name SET NOT NULL;
ALTER TABLE scenario_source_bundle ALTER COLUMN rulebook_edition SET NOT NULL;
