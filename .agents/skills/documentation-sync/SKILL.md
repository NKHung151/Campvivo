---
name: documentation-sync
description: Đồng bộ docs/ với code sau khi hành vi nghiệp vụ, API, hoặc schema thay đổi.
---

# Documentation Sync

## Khi nào dùng
Ngay sau khi: đổi API contract, đổi schema DB, đổi quy tắc nghiệp vụ, thêm/xoá trang.

## Quy trình
1. API đổi → cập nhật `docs/02-ky-thuat/api.md`.
2. Schema đổi → cập nhật ERD trong `docs/02-ky-thuat/database.md`.
3. Trang/route đổi → cập nhật `docs/03-san-pham/pages.md`.
4. Quy tắc nghiệp vụ đổi (vd. đổi % cọc, đổi điều kiện áp mã giảm giá) → cập nhật
   `docs/03-san-pham/features.md` và/hoặc `database.md` phần quy tắc nghiệp vụ.
5. Quyết định kỹ thuật quan trọng (đổi thư viện, đổi cách lock, đổi kiến trúc module) →
   ghi 1 mục ngắn vào `docs/99-ghi-chu/decisions.md` (ngày, quyết định, lý do).

## Tiêu chí hoàn thành
- `docs/` phản ánh đúng trạng thái code hiện tại — người khác (hoặc agent khác) đọc docs là
  đủ, không cần đọc lại toàn bộ code để hiểu hành vi hệ thống.
