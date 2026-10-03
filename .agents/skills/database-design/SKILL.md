---
name: database-design
description: Thiết kế/thay đổi schema PostgreSQL — entity, migration Flyway, index, đúng
  18 bảng nền (Tier 1 + Tier 2) đã chốt trong docs/02-ky-thuat/database.md.
---

# Database Design

## Khi nào dùng
Khi thêm bảng, thêm cột, đổi kiểu dữ liệu, hoặc thêm index.

## Quy trình
1. Đối chiếu với ERD hiện có trong `docs/02-ky-thuat/database.md` — tránh tạo bảng trùng
   mục đích.
2. Viết migration mới: `backend/src/main/resources/db/migration/V<n>__<mo_ta>.sql`.
   KHÔNG sửa file migration đã merge vào `dev`/`main`.
3. Cột liên quan tiền (`price`, `amount`...) dùng `NUMERIC(12,2)`, không dùng `FLOAT`.
4. Cột trạng thái dùng `VARCHAR` + `CHECK` constraint hoặc enum ở tầng Java
   (`@Enumerated(EnumType.STRING)`) — không để tự do.
5. Thêm index cho mọi FK dùng để JOIN/filter thường xuyên (xem danh sách index hiện có
   trong `database.md` để theo đúng quy ước đặt tên `idx_<bang>_<cot>`).
6. Sau khi đổi schema → cập nhật ERD trong `docs/02-ky-thuat/database.md`.

## Tiêu chí hoàn thành
- Migration chạy được từ đầu trên DB sạch.
- ERD trong docs đã cập nhật khớp thực tế.
