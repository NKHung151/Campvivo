---
name: api-contract
description: Giữ API contract giữa frontend và backend đồng bộ — cập nhật
  docs/02-ky-thuat/api.md trước khi code cả 2 phía.
---

# API Contract

## Khi nào dùng
Khi thêm, sửa, hoặc xoá bất kỳ endpoint nào.

## Quy trình
1. Cập nhật `docs/02-ky-thuat/api.md`: method, path, request/response JSON mẫu, mã lỗi dự
   kiến — TRƯỚC khi code.
2. Đảm bảo field name khớp giữa response thật và `frontend/src/types/`.
3. Mọi response lỗi theo format thống nhất: `{ "success": false, "errorCode": "...",
   "message": "...", "details": {...} }` (`api.md` mục 1). Mã lỗi mới phải thêm vào danh mục
   `api.md` mục 2 và enum `ErrorCode` backend, kèm HTTP status.
4. Kiểu dùng chung (`ItemSummary`, `OrderDetail`...) định nghĩa một lần ở `api.md` mục 3 / mục 4 và
   tái sử dụng; tiền là số nguyên, thời gian ISO-8601 có múi giờ.
5. Cập nhật cột API trong `docs/03-san-pham/use-cases.md` và `pages.md` nếu endpoint mới phục vụ
   use case/trang nào.
6. Nếu đổi contract của endpoint đã có người dùng (FE đã code) → báo trong plan, không âm
   thầm đổi field.

## Tiêu chí hoàn thành
- `api.md` phản ánh đúng những gì backend trả về thật.
- Frontend types khớp 100% với response thật.
