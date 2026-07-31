CREATE TABLE IF NOT EXISTS adventure_campaign_plan (
    session_id UUID PRIMARY KEY REFERENCES adventure_session(session_id) ON DELETE CASCADE,
    scenario_package_id UUID NOT NULL,
    scenario_package_revision BIGINT NOT NULL CHECK (scenario_package_revision > 0),
    plan_revision BIGINT NOT NULL CHECK (plan_revision > 0),
    campaign_plan_json JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS adventure_campaign_plan_scenario_package_idx
    ON adventure_campaign_plan (scenario_package_id, scenario_package_revision);
