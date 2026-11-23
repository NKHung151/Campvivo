-- V16 (D-41): yêu cầu trả hàng có lý do chọn sẵn, mô tả, 1–2 ảnh, số tiền hoàn; duyệt là hoàn tất (bỏ bước COMPLETED).
-- Bảng chưa có dữ liệu (module trả hàng chưa từng ghi) nên đổi kiểu / ràng buộc trực tiếp.

-- Lý do = mã trong danh sách cố định (ReturnReason); mô tả thêm của khách để riêng
ALTER TABLE return_requests ALTER COLUMN reason TYPE VARCHAR(30);
ALTER TABLE return_requests ADD CONSTRAINT chk_return_requests_reason CHECK (reason IN (
    'MISSING_ITEMS', 'WRONG_ITEM', 'DAMAGED', 'DEFECTIVE', 'EXPIRED', 'NOT_AS_DESCRIBED', 'USED', 'COUNTERFEIT',
    'CHANGED_MIND'));
ALTER TABLE return_requests ADD COLUMN description VARCHAR(2000);
-- URL ảnh khách chụp / tải lên (Cloudinary), 1–2 ảnh
ALTER TABLE return_requests ADD COLUMN image_urls JSONB NOT NULL;
ALTER TABLE return_requests ADD CONSTRAINT chk_return_requests_images
    CHECK (jsonb_typeof(image_urls) = 'array' AND jsonb_array_length(image_urls) BETWEEN 1 AND 2);
-- Số tiền hoàn lúc gửi yêu cầu = tạm tính − giảm giá (phí vận chuyển không hoàn); hoàn tiền làm ngoài hệ thống (D-07)
ALTER TABLE return_requests ADD COLUMN refund_amount NUMERIC(19, 0) NOT NULL;

-- PENDING → APPROVED (cộng lại kho, đơn RETURNED) | REJECTED
ALTER TABLE return_requests DROP CONSTRAINT return_requests_status_check;
ALTER TABLE return_requests ADD CONSTRAINT chk_return_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));
CREATE INDEX idx_return_requests_status ON return_requests (status, created_at);
