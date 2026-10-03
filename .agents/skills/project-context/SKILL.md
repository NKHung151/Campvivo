---
name: project-context
description: Nạp đúng ngữ cảnh dự án Campvivo trước khi làm bất kỳ việc gì — đọc tài liệu,
  xác định module, tránh code trùng lặp hoặc sai kiến trúc.
---

# Project Context

## Khi nào dùng
Luôn luôn là bước đầu tiên của bất kỳ task nào (trừ việc cực nhỏ như sửa 1 dòng CSS).

## Quy trình
1. Đọc `AGENTS.md` — xác định task thuộc module nào, tier nào (1/2/3).
2. Đọc tài liệu tương ứng theo bảng "Bản đồ tài liệu" trong `AGENTS.md` mục 4.
3. Tìm code đã tồn tại liên quan (`grep`/tìm theo tên entity, service, route) — không giả định
   chưa có gì.
4. Kiểm tra `docs/99-ghi-chu/decisions.md` xem có quyết định nào ảnh hưởng task này không.
5. Nếu task đụng > 1 module hoặc đổi contract DB/API → dừng lại, chuyển sang skill
   `implementation-planning` trước khi code.

## Tiêu chí hoàn thành
- Biết chính xác module, tier, file liên quan trước khi gõ dòng code đầu tiên.
- Không tạo lại entity/service/component đã tồn tại.
