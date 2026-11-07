-- V6 (F-07): voucher và chiến dịch giảm giá — docs/02-ky-thuat/database.md mục 3.14–3.16, index mục 3.23.
-- vouchers.is_public: D-29. Giá sau chiến dịch đọc qua Product.salePrice (@Formula, D-30).

-- 3.14 Voucher (BR-5)
CREATE TABLE vouchers (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            VARCHAR(50)    NOT NULL UNIQUE,
    discount_type   VARCHAR(30)    NOT NULL CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    discount_value  NUMERIC(19, 0) NOT NULL,
    max_discount    NUMERIC(19, 0) CHECK (max_discount > 0),
    min_order_value NUMERIC(19, 0) NOT NULL DEFAULT 0 CHECK (min_order_value >= 0),
    quantity        INT            NOT NULL CHECK (quantity > 0),
    used_count      INT            NOT NULL DEFAULT 0,
    start_date      TIMESTAMPTZ    NOT NULL,
    expiry_date     TIMESTAMPTZ    NOT NULL,
    is_active       BOOLEAN        NOT NULL DEFAULT TRUE,
    is_public       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_vouchers_code_upper CHECK (code = upper(code)),
    CONSTRAINT chk_vouchers_dates CHECK (expiry_date > start_date),
    CONSTRAINT chk_vouchers_used_count CHECK (used_count >= 0 AND used_count <= quantity),
    CONSTRAINT chk_vouchers_discount CHECK (
        (discount_type = 'PERCENTAGE' AND discount_value BETWEEN 1 AND 100)
        OR (discount_type = 'FIXED_AMOUNT' AND discount_value > 0 AND max_discount IS NULL))
);

-- 3.15 Chiến dịch giảm giá theo sản phẩm (BR-6)
CREATE TABLE discount_campaigns (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name           VARCHAR(255)   NOT NULL,
    slug           VARCHAR(280)   NOT NULL UNIQUE,
    description    TEXT,
    banner_url     VARCHAR(500),
    discount_type  VARCHAR(30)    NOT NULL CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    discount_value NUMERIC(19, 0) NOT NULL,
    start_date     TIMESTAMPTZ    NOT NULL,
    end_date       TIMESTAMPTZ    NOT NULL,
    is_active      BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_discount_campaigns_dates CHECK (end_date > start_date),
    CONSTRAINT chk_discount_campaigns_discount CHECK (
        (discount_type = 'PERCENTAGE' AND discount_value BETWEEN 1 AND 100)
        OR (discount_type = 'FIXED_AMOUNT' AND discount_value > 0))
);
CREATE INDEX idx_discount_campaigns_active_dates ON discount_campaigns (is_active, start_date, end_date);

-- 3.16 Sản phẩm thuộc chiến dịch (không áp combo)
CREATE TABLE discount_campaign_products (
    campaign_id BIGINT NOT NULL REFERENCES discount_campaigns (id) ON DELETE CASCADE,
    product_id  BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    PRIMARY KEY (campaign_id, product_id)
);
CREATE INDEX idx_discount_campaign_products_product_id ON discount_campaign_products (product_id);

-- FK còn thiếu từ V5 (idx_orders_voucher_id đã có ở V5)
ALTER TABLE orders ADD CONSTRAINT fk_orders_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers (id);
