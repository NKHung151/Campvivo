-- Chatbot (D-28): extension cho embedding (pgvector) và tìm kiếm tiếng Việt có/không dấu.
-- Cần image pgvector/pgvector:pg16 (D-27).
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;
