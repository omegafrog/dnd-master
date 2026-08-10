ALTER TABLE rule_evidence_unit
    ADD CONSTRAINT rule_evidence_unit_scope_unique UNIQUE (evidence_id, document_id, extraction_version);

ALTER TABLE rule_evidence_edge
    ADD COLUMN document_id UUID,
    ADD COLUMN extraction_version BIGINT;

UPDATE rule_evidence_edge edge
SET document_id = unit.document_id,
    extraction_version = unit.extraction_version
FROM rule_evidence_unit unit
WHERE unit.evidence_id = edge.from_evidence_id;

ALTER TABLE rule_evidence_edge
    ALTER COLUMN document_id SET NOT NULL,
    ALTER COLUMN extraction_version SET NOT NULL,
    ADD CONSTRAINT rule_evidence_edge_scope_unique UNIQUE (edge_id, document_id, extraction_version),
    ADD CONSTRAINT rule_evidence_edge_from_scope_fk FOREIGN KEY (from_evidence_id, document_id, extraction_version)
        REFERENCES rule_evidence_unit(evidence_id, document_id, extraction_version),
    ADD CONSTRAINT rule_evidence_edge_to_scope_fk FOREIGN KEY (to_evidence_id, document_id, extraction_version)
        REFERENCES rule_evidence_unit(evidence_id, document_id, extraction_version);

ALTER TABLE rule_evidence_source_span
    ADD CONSTRAINT rule_evidence_source_span_scope_fk
        FOREIGN KEY (evidence_id, document_id, extraction_version)
        REFERENCES rule_evidence_unit(evidence_id, document_id, extraction_version);

ALTER TABLE rule_evidence_edge_source_span
    ADD CONSTRAINT rule_evidence_edge_source_span_scope_fk
        FOREIGN KEY (edge_id, document_id, extraction_version)
        REFERENCES rule_evidence_edge(edge_id, document_id, extraction_version);

ALTER TABLE rule_evidence_search_index
    ADD CONSTRAINT rule_evidence_search_scope_fk
        FOREIGN KEY (evidence_id, document_id, extraction_version)
        REFERENCES rule_evidence_unit(evidence_id, document_id, extraction_version);
