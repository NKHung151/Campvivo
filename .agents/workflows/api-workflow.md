# Workflow: Thêm/sửa API endpoint

1. **project-context** — xác định module, đọc `docs/02-ky-thuat/api.md` phần liên quan.
2. **api-contract** — cập nhật `api.md` trước: method, path, request/response mẫu, mã lỗi.
3. **database-design** (nếu endpoint cần bảng/cột mới) — migration trước khi code service.
4. **backend-development** — implement theo `.agents/rules/backend.md`, đặc biệt transaction/
   lock nếu đụng tồn kho hoặc trạng thái đơn hàng.
5. **testing-qa** — test happy path + ít nhất 1 nhánh lỗi chính.
6. Nếu frontend đã dùng endpoint cũ với contract khác → báo trong PR, không âm thầm đổi field
   response.
7. **code-review** → **git-safety**.
