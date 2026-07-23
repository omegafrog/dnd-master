CREATE TABLE scenario_compilation (
    compilation_id UUID PRIMARY KEY,
    bundle_id UUID NOT NULL REFERENCES scenario_source_bundle(bundle_id),
    bundle_revision BIGINT NOT NULL CHECK (bundle_revision >= 1),
    input_fingerprint TEXT NOT NULL,
    status TEXT NOT NULL,
    attempt INT NOT NULL CHECK (attempt >= 0),
    lease_token UUID,
    package_id UUID REFERENCES scenario_package(package_id),
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX scenario_compilation_fingerprint_idx
    ON scenario_compilation(input_fingerprint);

CREATE TABLE adventure_work_job (
    work_id UUID PRIMARY KEY,
    work_type TEXT NOT NULL,
    aggregate_id UUID NOT NULL,
    input_version BIGINT NOT NULL CHECK (input_version >= 1),
    idempotency_key TEXT NOT NULL UNIQUE,
    attempt INT NOT NULL CHECK (attempt >= 0),
    status TEXT NOT NULL,
    delivery_token UUID,
    worker_id TEXT,
    lease_until TIMESTAMPTZ,
    failure_reason TEXT
);

CREATE INDEX adventure_work_job_pending_idx
    ON adventure_work_job(status, lease_until);
