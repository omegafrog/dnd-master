CREATE TABLE IF NOT EXISTS adventure_session_knowledge_document (
    session_id UUID NOT NULL REFERENCES adventure(session_id) ON DELETE CASCADE,
    selection_order INT NOT NULL CHECK (selection_order >= 0),
    knowledge_document_id UUID NOT NULL,
    PRIMARY KEY (session_id, knowledge_document_id),
    UNIQUE (session_id, selection_order)
);

CREATE INDEX IF NOT EXISTS adventure_session_knowledge_document_session_idx
    ON adventure_session_knowledge_document(session_id);
