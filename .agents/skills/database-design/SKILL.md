---
name: database-design
description: Thiết kế/thay đổi schema PostgreSQL — entity, migration Flyway, index — đúng 22 bảng
  ánh xạ 1-1 với báo cáo Phase1, đặc tả trong docs/02-ky-thuat/database.md.
---

# Database Design

## Khi nào dùng
Khi viết migration, tạo/sửa JPA entity, thêm cột, đổi kiểu dữ liệu, hoặc thêm index.

## Quy trình
1. Đọc `docs/02-ky-thuat/database.md` (mục 1 quy ước, mục 3 đặc tả bảng). Entity và migration phải
   khớp đúng tên cột, kiểu, nullable, CHECK, unique đã đặc tả — không tự thêm/bớt.
2. **Không thêm bảng mới** ngoài 22 bảng (decisions D-12). Cần lưu thêm dữ liệu → đề xuất thêm cột,
   hỏi người dùng, ghi vào `decisions.md`, cập nhật `database.md` (bảng ánh xạ mục 2 + đặc tả mục 3)
   TRƯỚC khi viết migration.
3. Viết migration mới: `backend/src/main/resources/db/migration/V<n>__<mo_ta>.sql`.
   KHÔNG sửa file migration đã merge vào `dev`/`main` (ngoại lệ một lần: thay V1/V2 placeholder — D-14).
4. Quy ước bắt buộc:
   - Tên bảng snake_case số nhiều; PK `BIGINT GENERATED ALWAYS AS IDENTITY`; `timestamptz`.
   - Tiền `NUMERIC(19,0)`; không dùng `FLOAT`/`DOUBLE`.
   - Enum: `VARCHAR(30)` + `CHECK (col IN (...))`, Java `@Enumerated(EnumType.STRING)`.
   - Bảng đa hình (`images`, `cart_items`, `order_items`, `reviews`, `favorites`, `inventory_logs`):
     `CHECK (num_nonnulls(...) = 1)` + unique một phần (`WHERE col IS NOT NULL`).
   - CHECK tồn kho trên `products` và `product_combos`: `0 ≤ reserved_quantity ≤ stock_quantity`.
5. Index cho mọi FK và các index liệt kê ở `database.md` mục 3.23, đặt tên `idx_<bang>_<cot>`.
6. Seed dev tách migration riêng chỉ chạy profile dev (`database.md` mục 7).

## Tiêu chí hoàn thành
- Migration chạy được từ đầu trên DB sạch; app khởi động với `ddl-auto=validate` không lỗi.
- `database.md` khớp thực tế (bảng ánh xạ + đặc tả).
