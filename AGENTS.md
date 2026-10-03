# AGENTS.md — Campvivo AI Development Contract

Entry point cho MỌI coding agent làm việc trên repo này (Claude Code, Codex, Antigravity, Cursor...).
Đọc file này trước, luôn luôn.

## 1. Dự án là gì
Campvivo là hệ thống TMĐT bán trang thiết bị dã ngoại (Spring Boot + Next.js + PostgreSQL),
đồ án tốt nghiệp nhóm 4 người. Chi tiết: `docs/01-tong-quan/project-overview.md`.

Phạm vi chia 3 tầng — LUÔN kiểm tra một tính năng thuộc tier nào trước khi code:
- **Tier 1 (bắt buộc)**: auth, catalog, cart, order, payment, admin cơ bản.
- **Tier 2 (điểm cộng)**: promotion, combo, review, notification, analytics, recommendation.
- **Tier 3 (phụ lục, mở sau tuần 9)**: rental — KHÔNG code nếu chưa được yêu cầu rõ ràng.

Xem `docs/03-san-pham/features.md` để biết tier của từng chức năng.

## 2. Trước khi code — bắt buộc theo thứ tự
0. Đọc `.agents/progress.md` — xem agent/phiên trước đã làm gì, còn dang dở gì, tránh làm lại.
1. Đọc `docs/` liên quan đến module đang đụng vào (xem mục 4 bên dưới để biết đọc gì).
2. Xác định module bị ảnh hưởng (`backend/src/main/java/vn/campvivo/<module>/`,
   `frontend/src/features/<module>/` hoặc tương đương).
3. Kiểm tra code đã tồn tại trong module đó — KHÔNG tạo lại chức năng đã có.
4. Với thay đổi lớn (>1 module, thêm bảng DB, đổi API contract): tạo plan trong
   `.agents/plans/active/` theo `.agents/plans/_template.md` trước khi code.
5. Dùng skill phù hợp trong `.agents/skills/` — xem mục 5.

## 3. Quy tắc code chung
- Ưu tiên file nhỏ, rõ trách nhiệm. Không tạo abstraction khi chưa cần.
- Không hardcode dữ liệu nghiệp vụ (giá, tỉ lệ cọc, số ngày...) — đưa vào config/entity.
- Tái sử dụng component/service đã có trước khi viết mới.
- Mỗi module backend và mỗi feature frontend độc lập, hạn chế phụ thuộc chéo.
- Không commit secrets (`.env`, API key MoMo/VNPay/Cloudinary/SMTP).
- Chi tiết theo stack: `.agents/rules/backend.md`, `.agents/rules/frontend.md`.

## 4. Bản đồ tài liệu (đọc gì cho việc gì)
| Muốn làm... | Đọc trước |
|---|---|
| Hiểu tổng thể dự án, actor, tier | `docs/01-tong-quan/`, `docs/03-san-pham/features.md` |
| Thêm/sửa API | `docs/02-ky-thuat/api.md`, `.agents/skills/api-contract/SKILL.md` |
| Đụng vào DB/entity | `docs/02-ky-thuat/database.md`, `.agents/skills/database-design/SKILL.md` |
| Làm UI mới | `docs/03-san-pham/pages.md`, `docs/03-san-pham/ui-guidelines.md`, `.agents/skills/frontend-design/SKILL.md` |
| Auth/phân quyền | `docs/02-ky-thuat/authentication.md` |
| Chạy local/Docker | `docs/04-van-hanh/local-development.md`, `docs/04-van-hanh/docker.md` |
| Quyết định kỹ thuật đã chốt trước đó | `docs/99-ghi-chu/decisions.md` |

## 5. Skills có sẵn (`.agents/skills/`)
`project-context` · `requirements-analysis` · `implementation-planning` ·
`frontend-design` · `frontend-implementation` · `frontend-review` ·
`backend-development` · `api-contract` · `database-design` ·
`testing-qa` · `code-review` · `debugging` · `documentation-sync` · `git-safety`

Không phải việc nào cũng cần hết các skill. Sửa 1 dòng CSS: chỉ cần `frontend-implementation`.
Thêm 1 luồng nghiệp vụ mới (vd. combo sản phẩm): `requirements-analysis` →
`implementation-planning` → `database-design`/`api-contract` → `backend-development`/
`frontend-design`+`frontend-implementation` → `testing-qa` → `code-review` →
`documentation-sync`.

## 6. Workflow theo loại việc (`.agents/workflows/`)
- Tính năng mới full-stack → `feature-workflow.md`
- Chỉ UI/trang mới → `frontend-workflow.md`
- Thêm/sửa endpoint → `api-workflow.md`
- Sửa bug → `bugfix-workflow.md`

## 7. Trước khi coi là xong
- Chạy lệnh chuẩn thay vì tự đoán: `./scripts/agent-lint.sh` và `./scripts/agent-test.sh`
  (xem mục 11). Không tự chạy `mvn`/`npm` trực tiếp trừ khi debug 1 phần nhỏ.
- Kiểm tra UI ảnh hưởng (responsive, loading/empty/error state) nếu có đổi frontend.
- Nếu hành vi nghiệp vụ thay đổi → cập nhật `docs/` tương ứng (xem `documentation-sync`).
- Nếu đổi schema DB → thêm file Flyway mới trong `backend/src/main/resources/db/migration/`,
  KHÔNG sửa file migration đã merge.
- Thêm 1 mục ngắn vào `.agents/progress.md` (đã làm gì, còn dang dở gì) — xem mục 2.0.

## 8. Cấm tuyệt đối
Không bao giờ xoá hoặc ghi đè không kiểm soát:
- `.env*`, mọi file chứa secret
- `docs/`
- `backend/src/main/resources/db/migration/*` đã merge vào `dev`/`main`
- `infra/` (cấu hình Docker)
- `tests/`
- Lịch sử Git (không `force push` vào `dev`/`main`)

## 9. Phân công module (tham khảo, có thể lệch theo tiến độ thực tế)
| Người | Module backend | Module frontend |
|---|---|---|
| Người 1 | `cart`, `order`, `payment` | tính năng giỏ hàng/checkout/đơn hàng |
| Người 2 | `catalog`, `inventory` | trang sản phẩm/danh mục, admin sản phẩm |
| Người 3 | `auth`, `user`, hạ tầng chung | layout, `components/ui`, trang auth/tài khoản |
| Người 4 | `promotion`, `combo`, `review`, `notification`, `recommendation`, `analytics` | dashboard admin, UI combo/khuyến mãi/đánh giá |

## 10. Công cụ cụ thể
- **Claude Code**: `CLAUDE.md` ở root chỉ là shim trỏ sang file này — đọc `AGENTS.md`, theo
  đúng mục 2–7.
- **Codex / Antigravity / công cụ khác**: cũng đọc file này làm entrypoint duy nhất.
- Chỉ 1 bộ quy tắc (`AGENTS.md` + `.agents/`) dùng chung cho mọi agent, để tránh lệch nhau.

## 11. Lệnh chuẩn (`scripts/`)
Dùng các script này thay vì tự gõ `mvn`/`npm` — đảm bảo mọi agent/tool chạy đúng cùng 1 lệnh:
- `./scripts/agent-build.sh` — build cả backend và frontend.
- `./scripts/agent-lint.sh` — lint/compile check cả 2 phía.
- `./scripts/agent-test.sh` — chạy test (bao gồm integration test Testcontainers ở backend).
