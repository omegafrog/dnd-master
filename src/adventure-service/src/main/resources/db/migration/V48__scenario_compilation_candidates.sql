CREATE TABLE IF NOT EXISTS scenario_compilation_candidate (
    candidate_id UUID PRIMARY KEY,
    compilation_id UUID NOT NULL REFERENCES scenario_compilation(compilation_id) ON DELETE CASCADE,
    candidate_key TEXT NOT NULL,
    candidate_type TEXT NOT NULL,
    required BOOLEAN NOT NULL,
    completeness TEXT NOT NULL,
    validation_json TEXT NOT NULL DEFAULT '[]',
    recoverability TEXT NOT NULL,
    repair_attempt_count INT NOT NULL DEFAULT 0 CHECK (repair_attempt_count BETWEEN 0 AND 1),
    raw_resolution_ref TEXT,
    final_resolution_ref TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (compilation_id, candidate_key)
);

CREATE INDEX IF NOT EXISTS scenario_compilation_candidate_compilation_idx
    ON scenario_compilation_candidate(compilation_id, candidate_key);

ALTER TABLE scenario_package ADD COLUMN IF NOT EXISTS compilation_outcome TEXT;
UPDATE scenario_package
SET compilation_outcome = CASE report_status
    WHEN 'COMPLETE' THEN 'COMPLETE'
    WHEN 'PARTIAL' THEN 'COMPLETE_WITH_WARNINGS'
    ELSE 'FAILED'
END
WHERE compilation_outcome IS NULL;
ALTER TABLE scenario_package ALTER COLUMN compilation_outcome SET DEFAULT 'COMPLETE';
