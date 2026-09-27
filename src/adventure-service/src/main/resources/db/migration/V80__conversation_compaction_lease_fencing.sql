ALTER TABLE adventure_conversation_compaction_job
    ADD COLUMN IF NOT EXISTS lease_token UUID;

CREATE UNIQUE INDEX IF NOT EXISTS adventure_conversation_summary_source_range_uidx
    ON adventure_conversation_summary (adventure_id, source_start, source_end);
