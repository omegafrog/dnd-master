CREATE TABLE IF NOT EXISTS rule_evidence_unit (
    evidence_id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    extraction_version BIGINT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('RULE', 'RULE_CONTEXT', 'RAW')),
    content TEXT NOT NULL,
    visibility TEXT NOT NULL CHECK (visibility IN ('PLAYER_VISIBLE', 'GM_ONLY', 'UNKNOWN')),
    UNIQUE (evidence_id, document_id, extraction_version),
    FOREIGN KEY (document_id, extraction_version)
        REFERENCES extraction_version(document_id, version)
);

CREATE TABLE IF NOT EXISTS rule_evidence_edge (
    edge_id UUID PRIMARY KEY,
    from_evidence_id UUID NOT NULL REFERENCES rule_evidence_unit(evidence_id),
    to_evidence_id UUID NOT NULL REFERENCES rule_evidence_unit(evidence_id),
    document_id UUID NOT NULL,
    extraction_version BIGINT NOT NULL,
    edge_type TEXT NOT NULL CHECK (edge_type IN ('PARENT', 'RELATED')),
    CHECK (from_evidence_id <> to_evidence_id),
    UNIQUE (edge_id, document_id, extraction_version),
    FOREIGN KEY (from_evidence_id, document_id, extraction_version)
        REFERENCES rule_evidence_unit(evidence_id, document_id, extraction_version),
    FOREIGN KEY (to_evidence_id, document_id, extraction_version)
        REFERENCES rule_evidence_unit(evidence_id, document_id, extraction_version)
);

CREATE TABLE IF NOT EXISTS rule_evidence_source_span (
    evidence_id UUID NOT NULL REFERENCES rule_evidence_unit(evidence_id),
    document_id UUID NOT NULL,
    extraction_version BIGINT NOT NULL,
    span_id BIGINT NOT NULL,
    PRIMARY KEY (evidence_id, document_id, extraction_version, span_id),
    FOREIGN KEY (evidence_id, document_id, extraction_version)
        REFERENCES rule_evidence_unit(evidence_id, document_id, extraction_version),
    FOREIGN KEY (document_id, extraction_version, span_id)
        REFERENCES extraction_source_span(document_id, version, span_id)
);

CREATE TABLE IF NOT EXISTS rule_evidence_edge_source_span (
    edge_id UUID NOT NULL REFERENCES rule_evidence_edge(edge_id),
    document_id UUID NOT NULL,
    extraction_version BIGINT NOT NULL,
    span_id BIGINT NOT NULL,
    PRIMARY KEY (edge_id, document_id, extraction_version, span_id),
    FOREIGN KEY (edge_id, document_id, extraction_version)
        REFERENCES rule_evidence_edge(edge_id, document_id, extraction_version),
    FOREIGN KEY (document_id, extraction_version, span_id)
        REFERENCES extraction_source_span(document_id, version, span_id)
);

CREATE TABLE IF NOT EXISTS rule_evidence_search_index (
    evidence_id UUID PRIMARY KEY REFERENCES rule_evidence_unit(evidence_id) ON DELETE CASCADE,
    document_id UUID NOT NULL,
    extraction_version BIGINT NOT NULL,
    embedding public.vector,
    search_text tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
    content TEXT NOT NULL,
    FOREIGN KEY (evidence_id, document_id, extraction_version)
        REFERENCES rule_evidence_unit(evidence_id, document_id, extraction_version)
);

CREATE INDEX IF NOT EXISTS rule_evidence_scope_idx
    ON rule_evidence_unit(document_id, extraction_version, kind);
CREATE INDEX IF NOT EXISTS rule_evidence_search_text_idx
    ON rule_evidence_search_index USING gin(search_text);
