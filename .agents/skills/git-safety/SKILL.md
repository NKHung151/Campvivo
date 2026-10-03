---
name: git-safety
description: Thao tác Git an toàn — nhánh, commit, PR — tránh mất code hoặc phá vỡ dev/main.
---

# Git Safety

## Khi nào dùng
Trước mọi thao tác Git: tạo nhánh, commit, mở PR.

## Quy trình
1. Theo `.agents/rules/git.md`.
2. Luôn tạo nhánh `feature/<module>-<mo-ta>` từ `dev` mới nhất trước khi code.
3. Commit nhỏ, theo Conventional Commits, dễ revert riêng lẻ nếu cần.
4. Trước khi mở PR: đã chạy test + lint/build xanh (xem `testing-qa`).
5. KHÔNG tự ý: force push vào `dev`/`main`, xoá nhánh người khác, merge PR của mình mà
   chưa có review.
6. Nếu cần sửa file migration/`docs/`/`infra/` — kiểm tra kỹ vì đây là các khu vực nhạy cảm
   liệt kê trong `AGENTS.md` mục 8.

## Tiêu chí hoàn thành
- PR mở đúng từ nhánh feature vào `dev`, CI xanh, mô tả rõ thay đổi.
