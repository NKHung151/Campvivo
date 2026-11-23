-- V15 (D-40): giỏ hàng / đơn hàng lưu biến thể khách chọn; giữ chỗ / trừ kho theo cả biến thể và tổng sản phẩm.

-- Dòng giỏ sản phẩm luôn trỏ tới một biến thể (dòng combo thì không). Admin xoá biến thể (bỏ màu / size) → dòng giỏ
-- của biến thể đó bị xoá theo.
ALTER TABLE cart_items ADD COLUMN variant_id BIGINT REFERENCES product_variants (id) ON DELETE CASCADE;
-- Sản phẩm chỉ có 1 biến thể: gán biến thể đó
UPDATE cart_items ci SET variant_id = v.id
FROM product_variants v
WHERE ci.product_id = v.product_id
  AND (SELECT count(*) FROM product_variants x WHERE x.product_id = v.product_id) = 1;
-- Sản phẩm nhiều biến thể: không biết khách muốn màu / size nào → bỏ khỏi giỏ để khách chọn lại
DELETE FROM cart_items WHERE product_id IS NOT NULL AND variant_id IS NULL;
ALTER TABLE cart_items ADD CONSTRAINT chk_cart_items_variant CHECK ((product_id IS NULL) = (variant_id IS NULL));
-- Cùng sản phẩm khác màu / size là hai dòng giỏ
DROP INDEX uq_cart_items_cart_product;
CREATE UNIQUE INDEX uq_cart_items_cart_variant ON cart_items (cart_id, variant_id) WHERE variant_id IS NOT NULL;
CREATE INDEX idx_cart_items_variant_id ON cart_items (variant_id);

-- Dòng đơn: biến thể + tên biến thể lúc đặt (snapshot, BR-4). Đơn cũ để NULL — giữ chỗ / trừ kho theo tổng sản phẩm
-- như trước và vẫn phân bổ tay ở trang Tồn kho. Biến thể bị xoá sau này → variant_id NULL, tên snapshot vẫn còn.
ALTER TABLE order_items ADD COLUMN variant_id BIGINT REFERENCES product_variants (id) ON DELETE SET NULL;
ALTER TABLE order_items ADD COLUMN variant_title VARCHAR(255);
ALTER TABLE order_items ADD CONSTRAINT chk_order_items_variant CHECK (variant_id IS NULL OR product_id IS NOT NULL);
CREATE INDEX idx_order_items_variant_id ON order_items (variant_id);
