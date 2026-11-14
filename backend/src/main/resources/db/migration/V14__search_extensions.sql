-- V14 (FR-07, tìm kiếm): extension `unaccent` (bỏ dấu) và `pg_trgm` (khớp gần giống / chịu lỗi gõ sai) cùng chỉ mục trigram — docs/99-ghi-chu/decisions.md D-39.
-- Cả hai là extension "tin cậy" (PostgreSQL 13+): chủ cơ sở dữ liệu thường, không cần siêu quản trị, cũng tạo được.

CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- `unaccent` chỉ STABLE nên không đánh chỉ mục trực tiếp được: bọc bằng hàm IMMUTABLE (từ điển cố định `public.unaccent`).
CREATE OR REPLACE FUNCTION f_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT public.unaccent('public.unaccent', $1) $$;

-- Chỉ mục GIN trigram: tăng tốc `LIKE '%từ khoá%'` (khớp chứa) và các toán tử gần giống trên tên không dấu, viết thường.
CREATE INDEX idx_products_name_trgm ON products USING gin (f_unaccent(lower(name)) gin_trgm_ops);
CREATE INDEX idx_products_brand_trgm ON products USING gin (f_unaccent(lower(brand)) gin_trgm_ops);
CREATE INDEX idx_product_combos_name_trgm ON product_combos USING gin (f_unaccent(lower(name)) gin_trgm_ops);
CREATE INDEX idx_articles_title_trgm ON articles USING gin (f_unaccent(lower(title)) gin_trgm_ops);
