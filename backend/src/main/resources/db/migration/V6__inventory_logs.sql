-- V6 (N2-24): nhật ký tồn kho — docs/02-ky-thuat/database.md mục 3.22, BR-8.
-- Ghi mỗi lần nhập/xuất/điều chỉnh tay, đóng gói/tháo combo (D-27) và trả hàng (RETURN).
-- Thay đổi do vòng đời đơn hàng (giữ chỗ/xác nhận/huỷ) KHÔNG ghi ở đây.
-- V5 dành cho migration đơn hàng của Người 1 (feature/order-payment-service).
CREATE TABLE inventory_logs (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id      BIGINT       REFERENCES products (id),
    combo_id        BIGINT       REFERENCES product_combos (id),
    -- Người thực hiện
    user_id         BIGINT       NOT NULL REFERENCES users (id),
    -- Dương = nhập, âm = xuất
    change_quantity INT          NOT NULL CHECK (change_quantity <> 0),
    -- stock_quantity sau thay đổi
    stock_after     INT          NOT NULL CHECK (stock_after >= 0),
    reason          VARCHAR(30)  NOT NULL CHECK (reason IN ('IMPORT', 'EXPORT', 'ADJUST', 'RETURN')),
    note            VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_inventory_logs_item CHECK (num_nonnulls(product_id, combo_id) = 1)
);

CREATE INDEX idx_inventory_logs_product_id ON inventory_logs (product_id, created_at);
CREATE INDEX idx_inventory_logs_combo_id ON inventory_logs (combo_id, created_at);
CREATE INDEX idx_inventory_logs_created_at ON inventory_logs (created_at);
