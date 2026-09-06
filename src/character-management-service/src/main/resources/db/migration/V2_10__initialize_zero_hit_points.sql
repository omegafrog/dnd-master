-- Character creation must produce a playable sheet. Repair legacy sheets that
-- still have the old explicit zero value, but never revive a sheet changed at runtime.
UPDATE character_management.character_sheet
SET character_state = jsonb_set(
        character_state::jsonb,
        '{currentHitPoints}',
        to_jsonb((derived_statistics::jsonb ->> 'hitPointMaximum')::integer),
        true)
WHERE version = 0
  AND character_state IS NOT NULL
  AND character_state <> ''
  AND character_state IS JSON OBJECT
  AND derived_statistics IS JSON OBJECT
  AND (character_state::jsonb ->> 'currentHitPoints') ~ '^-?[0-9]+$'
  AND (derived_statistics::jsonb ->> 'hitPointMaximum') ~ '^[1-9][0-9]*$'
  AND (character_state::jsonb ->> 'currentHitPoints')::integer <= 0
  AND (derived_statistics::jsonb ->> 'hitPointMaximum')::integer > 0;
