ALTER TABLE adventure_runtime_binding
    ADD COLUMN IF NOT EXISTS readiness_status TEXT NOT NULL DEFAULT 'BLOCKED',
    ADD COLUMN IF NOT EXISTS readiness_blockers_json TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS readiness_warnings_json TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS readiness_retryable BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE adventure_runtime_binding
SET readiness_status = 'BLOCKED',
    readiness_blockers_json = CASE
        WHEN playability_blockers_json = '[]' THEN '["runtime readiness requires fresh preflight"]'
        ELSE playability_blockers_json
    END,
    readiness_warnings_json = playability_warnings_json,
    readiness_retryable = TRUE;
