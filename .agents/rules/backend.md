# Quy tắc Backend (Spring Boot)

- Kiến trúc: `Controller` (nhận request, validate DTO) → `Service` (`@Transactional`, nghiệp
  vụ) → `Repository` (Spring Data JPA). Controller không chứa logic nghiệp vụ.
- Mỗi module (`auth`, `catalog`, `cart`, `order`, `payment`, `promotion`, `combo`, `review`,
  `notification`, `recommendation`, `analytics`, `inventory`, `rental`) độc lập theo package,
  theo đúng cấu trúc trong `docs/02-ky-thuat/architecture.md`.
- Entity KHÔNG trả thẳng ra API — luôn qua DTO + mapper.
- Mọi thao tác ảnh hưởng tồn kho (`stock_quantity`, `reserved_quantity`) PHẢI nằm trong
  `@Transactional`, dùng `SELECT ... FOR UPDATE` (pessimistic lock), và khóa theo thứ tự
  `product_id` tăng dần để tránh deadlock. Xem logic mẫu trong
  `docs/02-ky-thuat/database.md`.
- Thay đổi schema: tạo file Flyway mới (`V<n>__<mo_ta>.sql`) trong
  `src/main/resources/db/migration/`. KHÔNG sửa migration đã merge vào `dev`/`main`.
- Trạng thái đơn hàng chỉ được chuyển theo đúng máy trạng thái trong
  `docs/02-ky-thuat/database.md` — không bỏ qua bước hoặc tự thêm trạng thái mới nếu
  chưa cập nhật tài liệu.
- Webhook thanh toán (MoMo/VNPay) phải idempotent: kiểm tra `transaction_id` đã xử lý
  chưa trước khi cập nhật.
- Validate input ở DTO (Bean Validation), không chỉ dựa vào constraint của DB.
- Không log thông tin nhạy cảm (mật khẩu, token, số thẻ/payload thanh toán đầy đủ).
