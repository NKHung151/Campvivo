# Workflow: Chỉ UI / trang mới (backend đã có sẵn API)

Dùng khi API đã tồn tại, chỉ cần làm/sửa giao diện.

1. **project-context** — đọc `docs/03-san-pham/pages.md`, `ui-guidelines.md`, API liên quan
   trong `docs/02-ky-thuat/api.md`.
2. **frontend-design** — layout, trạng thái loading/empty/error, responsive.
3. **frontend-implementation** — code theo `.agents/rules/frontend.md`.
4. **frontend-review** — checklist responsive/accessibility/trạng thái biên.
5. **documentation-sync** — cập nhật `pages.md` nếu route mới hoặc hành vi trang đổi.
6. **git-safety** — PR từ `feature/<module>-ui-<mo-ta>`.

Việc nhỏ (sửa CSS, đổi text) có thể bỏ qua bước 2 và 4, chỉ cần 1 và 3.
