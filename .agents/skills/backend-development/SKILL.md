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
4. Logic đụng tồn kho/thanh toán/trạng thái đơn: bắt buộc đọc phần máy trạng thái và công
   thức tồn kho trong `docs/02-ky-thuat/database.md`, dùng `@Transactional` + lock đúng thứ
   tự đã quy định.
5. Viết ít nhất 1 test cho nhánh happy path và 1 test cho nhánh lỗi chính (vd. hết hàng,
   mã giảm giá hết hạn).

## Tiêu chí hoàn thành
- `mvn test` xanh.
- Không có logic nghiệp vụ nằm trong Controller.
- Endpoint khớp `docs/02-ky-thuat/api.md`.
