# Workflow: Tính năng mới (full-stack)

Dùng khi thêm 1 tính năng chạm cả backend lẫn frontend (vd. "thêm combo sản phẩm",
"thêm luồng đánh giá sản phẩm").

1. **project-context** — đọc docs liên quan, xác định tier, module.
2. **requirements-analysis** — chốt use case, điều kiện, exception flow.
3. **implementation-planning** — tạo file trong `.agents/plans/active/`, liệt kê file sẽ
   đụng, thứ tự làm.
4. **database-design** (nếu cần bảng/cột mới) — viết migration, cập nhật ERD.
5. **api-contract** — cập nhật `docs/02-ky-thuat/api.md` trước khi code 2 phía.
6. **backend-development** — code Controller/Service/Repository + test.
7. **frontend-design** → **frontend-implementation** — thiết kế rồi code UI.
8. **testing-qa** — test tích hợp, test biên, (test race condition nếu đụng tồn kho).
9. **code-review** — tự review theo checklist trước khi mở PR.
10. **documentation-sync** — cập nhật `docs/` phản ánh đúng những gì vừa làm.
11. **git-safety** — mở PR từ `feature/<module>-<mo-ta>` vào `dev`.
12. Chuyển plan từ `active/` sang `completed/`.
