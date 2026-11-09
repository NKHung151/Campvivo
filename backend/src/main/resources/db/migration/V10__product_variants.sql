-- V10 (D-37): biến thể sản phẩm tối đa 2 chiều (vd. Màu sắc × Size), mọi biến thể cùng giá với sản phẩm.
-- products.stock_quantity / reserved_quantity GIỮ NGUYÊN: giỏ hàng / đơn hàng vẫn giữ chỗ và trừ kho theo tổng sản phẩm.
-- Tồn chi tiết theo biến thể ở product_variants; nhập/xuất/đóng gói đổi cả biến thể và tổng. Sản phẩm không có tuỳ chọn
-- có đúng 1 biến thể mặc định (option1/option2 NULL). Dữ liệu cũ: mỗi sản phẩm → 1 biến thể mặc định mang nguyên tồn kho.

-- Tuỳ chọn của sản phẩm ("Màu sắc", "Size"), tối đa 2
CREATE TABLE product_options (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id BIGINT      NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    name       VARCHAR(50) NOT NULL,
    position   SMALLINT    NOT NULL CHECK (position IN (1, 2)),
    CONSTRAINT uq_product_options_position UNIQUE (product_id, position)
);
CREATE UNIQUE INDEX uq_product_options_name ON product_options (product_id, lower(name));

-- Giá trị của một tuỳ chọn ("Tím", "S/M")
CREATE TABLE product_option_values (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    option_id BIGINT       NOT NULL REFERENCES product_options (id) ON DELETE CASCADE,
    value     VARCHAR(100) NOT NULL,
    position  SMALLINT     NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_product_option_values_value ON product_option_values (option_id, lower(value));

-- Biến thể = một tổ hợp giá trị, có tồn kho chi tiết
CREATE TABLE product_variants (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id        BIGINT       NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    option1_value_id  BIGINT       REFERENCES product_option_values (id),
    option2_value_id  BIGINT       REFERENCES product_option_values (id),
    -- Mã hàng nội bộ, không bắt buộc
    sku               VARCHAR(64)  UNIQUE,
    stock_quantity    INT          NOT NULL DEFAULT 0,
    -- Chưa dùng (= 0) cho tới khi giỏ hàng / đơn hàng lưu biến thể
    reserved_quantity INT          NOT NULL DEFAULT 0,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    position          INT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_product_variants_stock
        CHECK (stock_quantity >= 0 AND reserved_quantity >= 0 AND reserved_quantity <= stock_quantity),
    CONSTRAINT chk_product_variants_options CHECK (option2_value_id IS NULL OR option1_value_id IS NOT NULL)
);
-- Một tổ hợp chỉ có một biến thể (kể cả biến thể mặc định không có giá trị)
CREATE UNIQUE INDEX uq_product_variants_combination
    ON product_variants (product_id, COALESCE(option1_value_id, 0), COALESCE(option2_value_id, 0));
CREATE INDEX idx_product_variants_product_id ON product_variants (product_id);

-- Mỗi sản phẩm hiện có → 1 biến thể mặc định mang nguyên tồn kho (giữ chỗ vẫn tính ở sản phẩm)
INSERT INTO product_variants (product_id, stock_quantity, is_active, position, created_at, updated_at)
SELECT id, stock_quantity, TRUE, 0, created_at, updated_at FROM products;

-- Combo gồm đúng biến thể nào (đóng gói trừ kho biến thể đó)
ALTER TABLE combo_items ADD COLUMN variant_id BIGINT REFERENCES product_variants (id);
UPDATE combo_items ci SET variant_id = v.id FROM product_variants v WHERE v.product_id = ci.product_id;
ALTER TABLE combo_items ALTER COLUMN variant_id SET NOT NULL;
CREATE INDEX idx_combo_items_variant_id ON combo_items (variant_id);

-- Nhật ký kho của sản phẩm ghi theo biến thể (giữ product_id để lọc theo cả sản phẩm)
ALTER TABLE inventory_logs ADD COLUMN variant_id BIGINT REFERENCES product_variants (id);
UPDATE inventory_logs l SET variant_id = v.id FROM product_variants v WHERE v.product_id = l.product_id;
ALTER TABLE inventory_logs ADD CONSTRAINT chk_inventory_logs_variant CHECK ((product_id IS NULL) = (variant_id IS NULL));
CREATE INDEX idx_inventory_logs_variant_id ON inventory_logs (variant_id, created_at);
