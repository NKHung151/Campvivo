-- V18 (F-09, phần favorites): yêu thích sản phẩm / combo / bài viết — docs/02-ky-thuat/database.md mục 3.18, D-06, D-47.

-- 3.18 Yêu thích (đúng một đối tượng; một dòng / khách / đối tượng; xoá cứng)
CREATE TABLE favorites (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product_id BIGINT      REFERENCES products (id) ON DELETE CASCADE,
    combo_id   BIGINT      REFERENCES product_combos (id) ON DELETE CASCADE,
    article_id BIGINT      REFERENCES articles (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_favorites_one_item CHECK (num_nonnulls(product_id, combo_id, article_id) = 1)
);

-- Chống trùng + đích của INSERT … ON CONFLICT DO NOTHING (POST /favorites idempotent)
CREATE UNIQUE INDEX uq_favorites_user_product ON favorites (user_id, product_id) WHERE product_id IS NOT NULL;
CREATE UNIQUE INDEX uq_favorites_user_combo ON favorites (user_id, combo_id) WHERE combo_id IS NOT NULL;
CREATE UNIQUE INDEX uq_favorites_user_article ON favorites (user_id, article_id) WHERE article_id IS NOT NULL;

-- Danh sách của khách, mới thích trước
CREATE INDEX idx_favorites_user_id_created_at ON favorites (user_id, created_at DESC);
-- Người yêu thích một mặt hàng (FAVORITE_ON_SALE, FAVORITE_BACK_IN_STOCK) + FK
CREATE INDEX idx_favorites_product_id ON favorites (product_id) WHERE product_id IS NOT NULL;
CREATE INDEX idx_favorites_combo_id ON favorites (combo_id) WHERE combo_id IS NOT NULL;
CREATE INDEX idx_favorites_article_id ON favorites (article_id) WHERE article_id IS NOT NULL;
