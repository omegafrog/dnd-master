ALTER TABLE adventure_runtime_binding ADD COLUMN IF NOT EXISTS party_json TEXT NOT NULL DEFAULT '[]';
