-- V8 (F-09, phần reviews): đánh giá sản phẩm/combo — docs/02-ky-thuat/database.md mục 3.17, index mục 3.23, BR-10.
-- Cột kiểm duyệt (moderation_status, flagged_terms): D-33. Cột ẩn (is_hidden, hidden_*): D-31.
-- Điểm TB/số lượt đọc qua Product/ProductCombo.averageRating, reviewCount (@Formula, D-35).

-- 3.17 Đánh giá (1 đánh giá / user / mặt hàng; xoá cứng — D-09)
CREATE TABLE reviews (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT        NOT NULL REFERENCES users (id),
    product_id        BIGINT        REFERENCES products (id) ON DELETE CASCADE,
    combo_id          BIGINT        REFERENCES product_combos (id) ON DELETE CASCADE,
    rating            SMALLINT      NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content           VARCHAR(2000),
    moderation_status VARCHAR(20)   NOT NULL DEFAULT 'SAFE'
                                    CHECK (moderation_status IN ('SAFE', 'SENSITIVE', 'CLEARED')),
    flagged_terms     VARCHAR(500),
    is_hidden         BOOLEAN       NOT NULL DEFAULT FALSE,
    hidden_reason     VARCHAR(500),
    hidden_by         BIGINT        REFERENCES users (id),
    hidden_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_reviews_one_item CHECK (num_nonnulls(product_id, combo_id) = 1),
    CONSTRAINT chk_reviews_hidden CHECK (NOT is_hidden OR (hidden_reason IS NOT NULL AND hidden_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_reviews_user_product ON reviews (user_id, product_id) WHERE product_id IS NOT NULL;
CREATE UNIQUE INDEX uq_reviews_user_combo ON reviews (user_id, combo_id) WHERE combo_id IS NOT NULL;

-- Danh sách đánh giá (mới nhất trước) + subquery điểm TB theo mặt hàng (D-35)
CREATE INDEX idx_reviews_product_id_created_at ON reviews (product_id, created_at DESC) WHERE product_id IS NOT NULL;
CREATE INDEX idx_reviews_combo_id_created_at ON reviews (combo_id, created_at DESC) WHERE combo_id IS NOT NULL;
CREATE INDEX idx_reviews_user_id ON reviews (user_id);
CREATE INDEX idx_reviews_hidden_by ON reviews (hidden_by);
