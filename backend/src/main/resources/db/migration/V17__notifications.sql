-- V17 (F-09 phần notifications, N4-03): thông báo in-app — docs/02-ky-thuat/database.md mục 3.20, index mục 3.23 (D-42, D-43).
-- Fan-out: mỗi người nhận một dòng. Dòng gộp đang mở có group_key khác NULL (unique một phần theo người nhận);
-- loại cần xử lý có resolved_at / resolved_by (resolved_by NULL + resolved_at có giá trị = Hệ thống).
-- ref_id không có FK vì trỏ nhiều bảng (đơn, yêu cầu trả hàng, khách, sản phẩm, combo, voucher, chiến dịch).

CREATE TABLE notifications (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(30)   NOT NULL,
    title       VARCHAR(255)  NOT NULL,
    content     VARCHAR(1000) NOT NULL,
    link        VARCHAR(500),
    ref_type    VARCHAR(20),
    ref_id      BIGINT,
    group_key   VARCHAR(100),
    event_count INT           NOT NULL DEFAULT 1,
    is_read     BOOLEAN       NOT NULL DEFAULT FALSE,
    resolved_at TIMESTAMPTZ,
    resolved_by BIGINT        REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_notifications_type CHECK (type IN (
        'ORDER_STATUS', 'RETURN_STATUS', 'SUPPORT_MESSAGE', 'VOUCHER_NEW', 'FAVORITE_ON_SALE', 'FAVORITE_BACK_IN_STOCK',
        'ORDER_NEW', 'ORDER_PAID', 'RETURN_NEW', 'SUPPORT_NEW', 'LOW_STOCK', 'REVIEW_FLAGGED')),
    CONSTRAINT chk_notifications_ref_type CHECK (ref_type IS NULL OR ref_type IN (
        'ORDER', 'RETURN', 'CONVERSATION', 'PRODUCT', 'COMBO', 'VOUCHER', 'CAMPAIGN', 'REVIEW_QUEUE')),
    CONSTRAINT chk_notifications_event_count CHECK (event_count >= 1),
    CONSTRAINT chk_notifications_resolved CHECK (resolved_by IS NULL OR resolved_at IS NOT NULL)
);

CREATE INDEX idx_notifications_user_id_is_read ON notifications (user_id, is_read, created_at DESC);
CREATE INDEX idx_notifications_resolved_by ON notifications (resolved_by);
CREATE INDEX idx_notifications_open_ref ON notifications (type, ref_type, ref_id) WHERE resolved_at IS NULL;
CREATE UNIQUE INDEX uq_notifications_user_group ON notifications (user_id, group_key) WHERE group_key IS NOT NULL;

-- D-45: job công bố voucher / chiến dịch. Dữ liệu đã có coi như đã công bố, để lần chạy đầu không gửi hàng loạt mã/chiến dịch cũ.
ALTER TABLE vouchers ADD COLUMN announced_at TIMESTAMPTZ;
UPDATE vouchers SET announced_at = now();

ALTER TABLE discount_campaigns ADD COLUMN announced_at TIMESTAMPTZ;
UPDATE discount_campaigns SET announced_at = now();
