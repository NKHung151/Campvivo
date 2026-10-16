# Quy tắc Backend (Spring Boot)

- Kiến trúc: `Controller` (nhận request, validate DTO, phân quyền) → `Service` (`@Transactional`,
  nghiệp vụ) → `Repository` (Spring Data JPA). Controller không chứa logic nghiệp vụ.
- Module (`auth`, `user`, `catalog`, `combo`, `inventory`, `cart`, `order`, `payment`, `promotion`,
  `review`, `favorite`, `article`, `support`, `notification`, `recommendation`, `analytics`,
  `common`) độc lập theo package, đúng bảng trách nhiệm trong `docs/02-ky-thuat/architecture.md`.
  Không gọi Repository của module khác — gọi Service public của nó. Không có module `rental`.
- Entity KHÔNG trả thẳng ra API — luôn qua DTO. Field/kiểu khớp `docs/02-ky-thuat/api.md`.
- Schema khớp 100% `docs/02-ky-thuat/database.md`: bảng snake_case số nhiều, tiền
  `NUMERIC(19,0)` ↔ `BigDecimal` (HALF_UP), enum `@Enumerated(STRING)`, `ddl-auto=validate`.
- **Tồn kho** (`stock_quantity`, `reserved_quantity` của `products` VÀ `product_combos`): mọi thay
  đổi nằm trong `@Transactional`, khoá `SELECT ... FOR UPDATE` (`PESSIMISTIC_WRITE`), theo thứ tự
  toàn cục `orders → products (id tăng) → product_combos (id tăng) → vouchers`. Đúng bảng BR-2.
  Điều chỉnh thủ công / trả hàng ghi `inventory_logs` (BR-8).
- **Giá**: tính bằng một `PricingService` duy nhất (BR-4, BR-6) cho cả hiển thị và tạo đơn. Giỏ
  hàng không lưu giá. Tạo đơn so `expectedTotal` → `PRICE_CHANGED`.
- **Voucher**: khoá dòng voucher, kiểm tra lại toàn bộ điều kiện BR-5 rồi mới `used_count + 1`; trả
  lượt khi đơn CANCELLED/PAYMENT_FAILED. Không "đọc rồi ghi" ngoài khoá.
- **Trạng thái đơn / yêu cầu trả hàng**: chỉ chuyển theo máy trạng thái `database.md` mục 5,
  định nghĩa trong enum (`canTransitionTo`). Mọi lần đổi trạng thái đơn ghi `order_status_history`.
  Không thêm trạng thái nếu chưa cập nhật docs.
- **IPN VNPay**: kiểm chữ ký + số tiền trước mọi thứ; idempotent (payment không còn PENDING → bỏ
  qua; `transaction_id` unique); return URL không đổi trạng thái (BR-12).
- **Side-effect** (thông báo, email, đẩy WebSocket): phát `ApplicationEvent`, xử lý ở
  `@TransactionalEventListener(phase = AFTER_COMMIT)`; email `@Async`. Ngoại lệ duy nhất: email OTP
  gửi đồng bộ.
- **Quyền sở hữu**: lấy `userId` từ `Authentication`, không nhận từ request. Tài nguyên không thuộc
  user → 404 `NOT_FOUND`.
- **OTP**: sinh bằng `SecureRandom`, lưu BCrypt, không bao giờ trả về hay log mã (BR-11).
- **Lỗi**: ném `BusinessException(ErrorCode.X)`; mã lỗi phải có trong `api.md` mục 2.
- Thay đổi schema: tạo file Flyway mới (`V<n>__<mo_ta>.sql`) trong `src/main/resources/db/migration/`.
  KHÔNG sửa migration đã merge vào `dev`/`main` (ngoại lệ một lần cho V1/V2 placeholder — D-14).
- Validate input ở DTO (Bean Validation), không chỉ dựa vào constraint của DB. Sanitize HTML (mô tả
  sản phẩm, nội dung bài viết) trước khi lưu.
- Tham số nghiệp vụ đọc từ `@ConfigurationProperties("app")`, không hardcode.
- Không log thông tin nhạy cảm (mật khẩu, OTP, token, `vnp_SecureHash`, payload thanh toán đầy đủ).
