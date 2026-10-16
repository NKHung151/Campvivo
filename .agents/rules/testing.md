# Quy tắc Testing

- Luồng bắt buộc phải có integration test (Testcontainers, PostgreSQL thật — không mock DB):
  - Tạo đơn + giữ/trừ/giải phóng tồn kho cho **sản phẩm và combo** (BR-2).
  - Race condition: N request song song đặt cùng 1 sản phẩm còn ít hàng → tổng bán không vượt
    `stock_quantity`; lặp lại với combo; giỏ chứa nhiều mặt hàng chéo nhau (kiểm tra không deadlock).
  - Voucher còn 1 lượt, N request song song → đúng 1 đơn dùng được; huỷ đơn trả lại lượt (BR-5).
  - IPN VNPay: gọi 2 lần cùng giao dịch → xử lý 1 lần; sai chữ ký → từ chối, không đổi dữ liệu; sai
    số tiền → từ chối (BR-12).
  - Job huỷ đơn quá hạn giải phóng giữ chỗ và lượt voucher (BR-3).
  - Máy trạng thái đơn: chuyển hợp lệ thành công, chuyển sai → `INVALID_STATUS_TRANSITION`.
  - Trả hàng COMPLETED cộng đúng tồn kho + ghi log, đơn RETURNED; quá hạn → `RETURN_WINDOW_EXPIRED` (BR-9).
  - Đánh giá: chưa mua → `NOT_PURCHASED`; trùng → `REVIEW_ALREADY_EXISTS` (BR-10).
  - Giá: chiến dịch hiệu lực áp đúng, `expectedTotal` lệch → `PRICE_CHANGED` (BR-4, BR-6).
  - Auth: OTP sai/hết hạn/quá số lần; đăng nhập khi chưa xác thực; logout làm token cũ bị 401;
    truy cập đơn của người khác → 404; Employee gọi endpoint chỉ dành Admin → 403.
- Mỗi Service có ít nhất 1 test happy path và 1 test nhánh lỗi chính.
- Frontend: test/kiểm tra thủ công các state quan trọng của checkout (giỏ rỗng, hết hàng giữa chừng,
  voucher hết hạn, `PRICE_CHANGED`, thanh toán thất bại, IPN chưa về).
- Trước khi coi 1 task là xong: `./scripts/agent-lint.sh` và `./scripts/agent-test.sh` phải xanh.
- Không xoá hoặc comment-out test để "cho qua" CI.
