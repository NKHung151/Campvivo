-- Bảng riêng của chatbot — docs/02-ky-thuat/database.md mục 3.24. Chỉ đọc bảng nghiệp vụ, chỉ ghi các bảng này.

-- Tài liệu admin nạp cho bot
CREATE TABLE chatbot_documents (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title         VARCHAR(255)  NOT NULL,
    description   VARCHAR(1000),
    file_name     VARCHAR(255)  NOT NULL,
    content_type  VARCHAR(100)  NOT NULL,
    size_bytes    BIGINT        NOT NULL CHECK (size_bytes > 0),
    sha256        CHAR(64)      NOT NULL UNIQUE,
    content       BYTEA         NOT NULL,
    status        VARCHAR(30)   NOT NULL CHECK (status IN ('PROCESSING', 'READY', 'FAILED')),
    error_message VARCHAR(1000),
    chunk_count   INT           NOT NULL DEFAULT 0,
    is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by    BIGINT        NOT NULL REFERENCES users (id),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_chatbot_documents_created_by ON chatbot_documents (created_by);

-- Đoạn văn bản đã embed
CREATE TABLE chatbot_document_chunks (
    id          UUID PRIMARY KEY,
    document_id BIGINT       NOT NULL REFERENCES chatbot_documents (id) ON DELETE CASCADE,
    chunk_index INT          NOT NULL,
    content     TEXT         NOT NULL,
    metadata    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    embedding   vector(1536) NOT NULL,
    CONSTRAINT uq_chatbot_chunks_doc_index UNIQUE (document_id, chunk_index)
);
CREATE INDEX idx_chatbot_chunks_embedding ON chatbot_document_chunks USING hnsw (embedding vector_cosine_ops);

-- Embedding sản phẩm/combo (không FK: xoá mặt hàng không bị chặn; job dọn dòng của mặt hàng đã xoá)
CREATE TABLE chatbot_item_embeddings (
    item_type    VARCHAR(10)  NOT NULL CHECK (item_type IN ('PRODUCT', 'COMBO')),
    item_id      BIGINT       NOT NULL,
    content_hash CHAR(64)     NOT NULL,
    embedding    vector(1536) NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (item_type, item_id)
);
CREATE INDEX idx_chatbot_item_embeddings ON chatbot_item_embeddings USING hnsw (embedding vector_cosine_ops);

-- Hội thoại của khách với bot
CREATE TABLE chatbot_conversations (
    id         UUID PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title      VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_chatbot_conversations_user ON chatbot_conversations (user_id, updated_at DESC);

CREATE TABLE chatbot_messages (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id   UUID         NOT NULL REFERENCES chatbot_conversations (id) ON DELETE CASCADE,
    role              VARCHAR(10)  NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content           TEXT         NOT NULL,
    cards             JSONB        NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(cards) = 'array'),
    sources           JSONB        NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(sources) = 'array'),
    prompt_tokens     INT,
    completion_tokens INT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_chatbot_messages_conversation ON chatbot_messages (conversation_id, created_at);
