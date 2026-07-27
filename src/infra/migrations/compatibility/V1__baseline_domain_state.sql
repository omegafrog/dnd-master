CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE compat_adventure (
    adventure_id UUID PRIMARY KEY,
    owner_player_id UUID NOT NULL,
    current_scene TEXT NOT NULL,
    status TEXT NOT NULL
);

CREATE TABLE compat_rulebook_source (
    chunk_id UUID PRIMARY KEY,
    owner_player_id UUID NOT NULL,
    rulebook_id UUID NOT NULL,
    locator TEXT NOT NULL,
    content TEXT NOT NULL
);

CREATE TABLE compat_rulebook_vector_v1 (
    chunk_id UUID PRIMARY KEY REFERENCES compat_rulebook_source(chunk_id) ON DELETE CASCADE,
    embedding vector(3) NOT NULL
);

CREATE INDEX compat_rulebook_vector_v1_cosine_idx
    ON compat_rulebook_vector_v1 USING hnsw (embedding vector_cosine_ops);
