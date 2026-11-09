-- V12 (FR-21): bài viết — docs/02-ky-thuat/database.md mục 3.21, index mục 3.23.
-- Phân loại BLOG / JOURNAL / GUIDE phục vụ /blog, /journal, /guides (D-16). published_at gán ở lần xuất bản đầu tiên.

CREATE TABLE articles (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES users (id),
    title         VARCHAR(255)  NOT NULL,
    slug          VARCHAR(280)  NOT NULL UNIQUE,
    summary       VARCHAR(1000),
    content       TEXT          NOT NULL,
    thumbnail_url VARCHAR(500),
    category      VARCHAR(30)   NOT NULL CHECK (category IN ('BLOG', 'JOURNAL', 'GUIDE')),
    is_published  BOOLEAN       NOT NULL DEFAULT FALSE,
    published_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_articles_published_at CHECK (NOT is_published OR published_at IS NOT NULL)
);

CREATE INDEX idx_articles_user_id ON articles (user_id);
-- Tìm kiếm theo tiêu đề (ILIKE)
CREATE INDEX idx_articles_title_lower ON articles (lower(title));
-- Danh sách public: bài đã xuất bản theo loại, mới nhất trước
CREATE INDEX idx_articles_category_published_at ON articles (category, published_at DESC) WHERE is_published;
