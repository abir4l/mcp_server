-- Runs once, on the first start of an empty postgres volume.

CREATE EXTENSION IF NOT EXISTS vector;

-- One row per FAQ chunk. The embedding comes from Ollama's nomic-embed-text (768 dimensions),
-- computed over "search_document: <title>\n<content>". Queries must be embedded with the same
-- model and the "search_query: " prefix.
CREATE TABLE faq_chunks (
    id         bigserial PRIMARY KEY,
    category   text         NOT NULL,
    title      text         NOT NULL,
    content    text         NOT NULL,
    embedding  vector(768)  NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX faq_chunks_embedding_idx ON faq_chunks USING hnsw (embedding vector_cosine_ops);
