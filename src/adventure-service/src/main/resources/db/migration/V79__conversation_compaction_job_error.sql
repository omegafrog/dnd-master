ALTER TABLE adventure_conversation_compaction_job
    ADD COLUMN IF NOT EXISTS last_error TEXT;
