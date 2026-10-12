CREATE INDEX IF NOT EXISTS published_rag_chunk_embedding_1024_hnsw_idx
    ON published_rag_chunk USING hnsw ((embedding::vector(1024)) vector_cosine_ops)
    WHERE embedding_dimension = 1024;
