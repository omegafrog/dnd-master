CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;

CREATE TABLE IF NOT EXISTS rulebook_vector_index (
    index_id UUID PRIMARY KEY,
    rulebook_id UUID NOT NULL,
    owner_player_id UUID NOT NULL,
    embedding_model TEXT NOT NULL,
    dimension INTEGER NOT NULL CHECK (dimension > 0),
    index_version TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'EMBEDDING', 'READY', 'FAILED')),
    UNIQUE (rulebook_id, owner_player_id, embedding_model, index_version)
);

CREATE TABLE IF NOT EXISTS rulebook_vector_chunk (
    chunk_id UUID PRIMARY KEY,
    index_id UUID NOT NULL REFERENCES rulebook_vector_index(index_id) ON DELETE CASCADE,
    rulebook_id UUID NOT NULL,
    owner_player_id UUID NOT NULL,
    sequence INTEGER NOT NULL CHECK (sequence >= 0),
    locator TEXT NOT NULL,
    content TEXT NOT NULL,
    embedding public.vector NOT NULL,
    UNIQUE (index_id, sequence)
);

CREATE INDEX IF NOT EXISTS rulebook_vector_chunk_owner_rulebook_idx
    ON rulebook_vector_chunk (owner_player_id, rulebook_id);
