ALTER TABLE adventure_session
    ADD COLUMN IF NOT EXISTS blueprint_id UUID,
    ADD COLUMN IF NOT EXISTS blueprint_revision BIGINT;

UPDATE adventure_session
SET blueprint_id = scenario_package_id,
    blueprint_revision = 1
WHERE blueprint_id IS NULL;

ALTER TABLE adventure_session
    ALTER COLUMN blueprint_id SET NOT NULL,
    ALTER COLUMN blueprint_revision SET NOT NULL;
