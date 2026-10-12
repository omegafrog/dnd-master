ALTER TABLE rulebook_registration
    ADD COLUMN IF NOT EXISTS extraction_actor_player_id UUID;
