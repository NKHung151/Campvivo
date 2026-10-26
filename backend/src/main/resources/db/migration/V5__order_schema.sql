-- V5 (F-08): giỏ hàng, đơn hàng, thanh toán, trả hàng — docs/02-ky-thuat/database.md mục 3.7–3.13, index mục 3.23.
-- V3, V4 đã dùng cho dữ liệu mẫu (db/seed, chung một dãy số phiên bản).
-- orders.voucher_id chưa có FK: bảng vouchers thuộc migration promotion (F-07) — migration đó thêm
--   ALTER TABLE orders ADD CONSTRAINT fk_orders_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers (id);
-- Cột GHN trên orders (shipping_district_id, shipping_ward_code, shipping_order_code, shipping_order_error): D-27, D-28.

-- 3.7 Giỏ hàng: 1 giỏ / user
CREATE TABLE carts (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3.8 Dòng giỏ: sản phẩm HOẶC combo, không lưu giá (D-04)
CREATE TABLE cart_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cart_id    BIGINT      NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    product_id BIGINT REFERENCES products (id),
    combo_id   BIGINT REFERENCES product_combos (id),
    quantity   INT         NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_cart_items_one_item CHECK (num_nonnulls(product_id, combo_id) = 1)
);
CREATE UNIQUE INDEX uq_cart_items_cart_product ON cart_items (cart_id, product_id) WHERE product_id IS NOT NULL;
CREATE UNIQUE INDEX uq_cart_items_cart_combo ON cart_items (cart_id, combo_id) WHERE combo_id IS NOT NULL;
CREATE INDEX idx_cart_items_cart_id ON cart_items (cart_id);
CREATE INDEX idx_cart_items_product_id ON cart_items (product_id);
CREATE INDEX idx_cart_items_combo_id ON cart_items (combo_id);

-- 3.9 Đơn hàng
CREATE TABLE orders (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_code           VARCHAR(30)    NOT NULL UNIQUE,
    user_id              BIGINT         NOT NULL REFERENCES users (id),
    voucher_id           BIGINT,
    voucher_code         VARCHAR(50),
    subtotal             NUMERIC(19, 0) NOT NULL,
    discount_amount      NUMERIC(19, 0) NOT NULL DEFAULT 0,
    shipping_fee         NUMERIC(19, 0) NOT NULL DEFAULT 0,
    final_amount         NUMERIC(19, 0) NOT NULL,
    recipient_name       VARCHAR(100)   NOT NULL,
    recipient_phone      VARCHAR(20)    NOT NULL,
    shipping_address     VARCHAR(500)   NOT NULL,
    shipping_district_id INT            NOT NULL,
    shipping_ward_code   VARCHAR(20)    NOT NULL,
    shipping_order_code  VARCHAR(50) UNIQUE,
    shipping_order_error VARCHAR(500),
    payment_method       VARCHAR(30)    NOT NULL CHECK (payment_method IN ('VNPAY', 'COD')),
    status               VARCHAR(30)    NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPING', 'COMPLETED',
                                                                   'CANCELLED', 'PAYMENT_FAILED', 'RETURNED')),
    note                 VARCHAR(500),
    completed_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_orders_amounts CHECK (subtotal >= 0 AND discount_amount >= 0 AND discount_amount <= subtotal
        AND shipping_fee >= 0 AND final_amount = subtotal - discount_amount + shipping_fee)
);
CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_voucher_id ON orders (voucher_id);
CREATE INDEX idx_orders_status_created_at ON orders (status, created_at);
CREATE INDEX idx_orders_completed_at ON orders (completed_at);

-- 3.10 Dòng đơn: snapshot lúc đặt, không bao giờ sửa (BR-4)
CREATE TABLE order_items (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id       BIGINT         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id     BIGINT REFERENCES products (id),
    combo_id       BIGINT REFERENCES product_combos (id),
    item_name      VARCHAR(255)   NOT NULL,
    item_image_url VARCHAR(500),
    original_price NUMERIC(19, 0) NOT NULL,
    unit_price     NUMERIC(19, 0) NOT NULL,
    quantity       INT            NOT NULL CHECK (quantity > 0),
    line_total     NUMERIC(19, 0) NOT NULL,
    CONSTRAINT chk_order_items_one_item CHECK (num_nonnulls(product_id, combo_id) = 1)
);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_product_id ON order_items (product_id);
CREATE INDEX idx_order_items_combo_id ON order_items (combo_id);

-- 3.11 Lịch sử trạng thái (BR-7); changed_by NULL = System
CREATE TABLE order_status_history (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id   BIGINT       NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    old_status VARCHAR(30),
    new_status VARCHAR(30)  NOT NULL,
    changed_by BIGINT REFERENCES users (id),
    note       VARCHAR(500),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_status_history_order_id ON order_status_history (order_id);
CREATE INDEX idx_order_status_history_changed_by ON order_status_history (changed_by);

-- 3.12 Thanh toán: 1 bản ghi / đơn
CREATE TABLE payments (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id       BIGINT         NOT NULL UNIQUE REFERENCES orders (id),
    method         VARCHAR(30)    NOT NULL CHECK (method IN ('VNPAY', 'COD')),
    amount         NUMERIC(19, 0) NOT NULL,
    status         VARCHAR(30)    NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    transaction_id VARCHAR(100) UNIQUE,
    payload        JSONB,
    paid_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- 3.13 Yêu cầu trả hàng: 1 yêu cầu / đơn
CREATE TABLE return_requests (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id     BIGINT        NOT NULL UNIQUE REFERENCES orders (id),
    reason       VARCHAR(1000) NOT NULL,
    status       VARCHAR(30)   NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED')),
    processed_by BIGINT REFERENCES users (id),
    processed_at TIMESTAMPTZ,
    note         VARCHAR(1000),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_return_requests_processed_by ON return_requests (processed_by);
