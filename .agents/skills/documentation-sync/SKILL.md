---
name: documentation-sync
description: Đồng bộ docs/ với code sau khi hành vi nghiệp vụ, API, hoặc schema thay đổi.
---

# Documentation Sync

## Khi nào dùng
Ngay sau khi: đổi API contract, đổi schema DB, đổi quy tắc nghiệp vụ, thêm/xoá trang.

## Quy trình
1. API đổi → cập nhật `docs/02-ky-thuat/api.md`.
2. Schema đổi → cập nhật `docs/02-ky-thuat/database.md` (bảng ánh xạ mục 2 + đặc tả mục 3).
   Use case/FR đổi → cập nhật `docs/03-san-pham/use-cases.md`.
3. Trang/route đổi → cập nhật `docs/03-san-pham/pages.md`.
4. Quy tắc nghiệp vụ đổi (vd. đổi thời hạn trả hàng, đổi điều kiện áp voucher) → cập nhật
   `docs/03-san-pham/features.md` và/hoặc `database.md` mục 4 (BR-xx), mục 5 (máy trạng thái).
   Thêm/đổi key cấu hình `app.*` → cập nhật bảng trong `tech-stack.md`.
5. Quyết định kỹ thuật quan trọng (đổi thư viện, đổi cách lock, đổi kiến trúc module) →
   ghi 1 mục ngắn vào `docs/99-ghi-chu/decisions.md` (ngày, quyết định, lý do).

6. Có chỗ docs lệch báo cáo Phase1 → nhắc người dùng cập nhật báo cáo (code và báo cáo phải khớp
   khi bảo vệ).

## Tiêu chí hoàn thành
- `docs/` phản ánh đúng trạng thái code hiện tại — người khác (hoặc agent khác) đọc docs là
  đủ, không cần đọc lại toàn bộ code để hiểu hành vi hệ thống.
