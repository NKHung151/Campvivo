-- V2 (F-06): cấu trúc bảng catalog — docs/02-ky-thuat/database.md mục 3.2–3.6, index mục 3.23.
-- `articles` và `inventory_logs` cần bảng `users` (F-05) nên nằm ở migration sau.

-- 3.2 Danh mục đa cấp: path = id từ gốc tới chính nó, dạng '/1/5/12/' (BR-13)
CREATE TABLE categories (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_id  BIGINT REFERENCES categories (id) ON DELETE RESTRICT,
    name       VARCHAR(150) NOT NULL,
    slug       VARCHAR(180) NOT NULL UNIQUE,
    path       VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 3.3 Sản phẩm — mỗi mặt hàng (vd. từng màu/size) là một sản phẩm riêng, không có biến thể (D-24)
CREATE TABLE products (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id       BIGINT        NOT NULL REFERENCES categories (id),
    name              VARCHAR(255)  NOT NULL,
    slug              VARCHAR(280)  NOT NULL UNIQUE,
    short_description VARCHAR(500),
    description       TEXT,
    brand             VARCHAR(100),
    price             NUMERIC(19, 0) NOT NULL CHECK (price > 0),
    -- Thông số: [{"name": "Chất liệu", "value": "Nhôm"}, ...] theo thứ tự hiển thị
    specs             JSONB         NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(specs) = 'array'),
    stock_quantity    INT           NOT NULL DEFAULT 0,
    reserved_quantity INT           NOT NULL DEFAULT 0,
    is_active         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_products_stock
        CHECK (stock_quantity >= 0 AND reserved_quantity >= 0 AND reserved_quantity <= stock_quantity)
);

-- 3.5 Combo — tồn kho riêng (D-03)
CREATE TABLE product_combos (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id       BIGINT        NOT NULL REFERENCES categories (id),
    name              VARCHAR(255)  NOT NULL,
    slug              VARCHAR(280)  NOT NULL UNIQUE,
    description       TEXT,
    combo_price       NUMERIC(19, 0) NOT NULL CHECK (combo_price > 0),
    stock_quantity    INT           NOT NULL DEFAULT 0,
    reserved_quantity INT           NOT NULL DEFAULT 0,
    is_active         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_product_combos_stock
        CHECK (stock_quantity >= 0 AND reserved_quantity >= 0 AND reserved_quantity <= stock_quantity)
);

-- 3.6 Thành phần combo — đóng gói combo trừ kho sản phẩm thành phần (D-22)
CREATE TABLE combo_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    combo_id   BIGINT NOT NULL REFERENCES product_combos (id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products (id),
    quantity   INT    NOT NULL CHECK (quantity > 0),
    CONSTRAINT uq_combo_items_combo_product UNIQUE (combo_id, product_id)
);

-- 3.4 Ảnh của sản phẩm hoặc combo (đúng 1 trong 2)
CREATE TABLE images (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id    BIGINT REFERENCES products (id) ON DELETE CASCADE,
    combo_id      BIGINT REFERENCES product_combos (id) ON DELETE CASCADE,
    image_url     VARCHAR(500) NOT NULL UNIQUE,
    is_primary    BOOLEAN      NOT NULL DEFAULT FALSE,
    display_order INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_images_owner CHECK (num_nonnulls(product_id, combo_id) = 1)
);
-- Mỗi mặt hàng tối đa 1 ảnh chính
CREATE UNIQUE INDEX uq_images_product_primary ON images (product_id) WHERE is_primary AND product_id IS NOT NULL;
CREATE UNIQUE INDEX uq_images_combo_primary ON images (combo_id) WHERE is_primary AND combo_id IS NOT NULL;

-- 3.23 Index
CREATE INDEX idx_categories_parent_id ON categories (parent_id);
CREATE INDEX idx_categories_path ON categories (path varchar_pattern_ops);
CREATE INDEX idx_products_category_id ON products (category_id);
CREATE INDEX idx_products_is_active_created_at ON products (is_active, created_at);
CREATE INDEX idx_products_name_lower ON products (lower(name));
CREATE INDEX idx_product_combos_category_id ON product_combos (category_id);
CREATE INDEX idx_product_combos_is_active_created_at ON product_combos (is_active, created_at);
CREATE INDEX idx_combo_items_product_id ON combo_items (product_id);
CREATE INDEX idx_images_product_id ON images (product_id);
CREATE INDEX idx_images_combo_id ON images (combo_id);
