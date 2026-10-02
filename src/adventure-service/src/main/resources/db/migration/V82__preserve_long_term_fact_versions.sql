CREATE TABLE IF NOT EXISTS adventure_long_term_fact_history (
    adventure_id UUID NOT NULL REFERENCES adventure(adventure_id) ON DELETE CASCADE,
    fact_id UUID NOT NULL,
    established_turn_id UUID NOT NULL,
    source_adventure_version BIGINT NOT NULL,
    fact_kind TEXT NOT NULL,
    relevance TEXT NOT NULL,
    player_visible BOOLEAN NOT NULL,
    fact_version BIGINT NOT NULL,
    archived_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (adventure_id, fact_id, fact_version),
    CHECK (source_adventure_version >= 0),
    CHECK (fact_version >= 1),
    CHECK (fact_kind IN ('EVENT', 'RELATIONSHIP', 'GOAL', 'THREAT'))
);
