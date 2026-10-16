---
name: backend-development
description: Triển khai API và business logic Spring Boot theo đúng kiến trúc module,
  đặc biệt với các luồng chạm tồn kho/thanh toán/trạng thái đơn hàng.
---

# Backend Development

## Khi nào dùng
Khi thêm/sửa Controller, Service, Repository, Entity ở backend.

## Quy trình
1. Theo `.agents/rules/backend.md` và kiến trúc trong `docs/02-ky-thuat/architecture.md`.
2. Entity mới → cân nhắc cùng skill `database-design` nếu đổi schema.
3. Endpoint mới → đối chiếu/khai báo trong `docs/02-ky-thuat/api.md` trước (skill
   `api-contract`), không tự chế payload khác với đã thống nhất.
4. Logic đụng tồn kho (sản phẩm + combo), giá, voucher, thanh toán, trạng thái đơn/trả hàng: bắt
   buộc đọc `docs/02-ky-thuat/database.md` mục 4 (BR-xx), mục 5 (máy trạng thái), mục 6 (thứ tự
   khoá), dùng `@Transactional` + khoá đúng thứ tự `orders → products → product_combos → vouchers`.
   Giá chỉ tính qua `PricingService`; side-effect (thông báo/email) qua event AFTER_COMMIT.
5. Đọc mô tả FR tương ứng trong `docs/03-san-pham/features.md` để xử lý đủ luồng ngoại lệ và mã lỗi.
6. Viết ít nhất 1 test cho nhánh happy path và 1 test cho nhánh lỗi chính (vd. hết hàng,
   voucher hết lượt, chuyển trạng thái sai).

## Tiêu chí hoàn thành
- `./scripts/agent-test.sh` xanh.
- Không có logic nghiệp vụ nằm trong Controller.
- Endpoint khớp `docs/02-ky-thuat/api.md`.
