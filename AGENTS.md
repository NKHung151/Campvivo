# AGENTS.md — Campvivo AI Development Contract

Entry point cho MỌI coding agent làm việc trên repo này (Claude Code, Codex, Antigravity, Cursor...).
Đọc file này trước, luôn luôn.

## 1. Dự án là gì
Campvivo là hệ thống TMĐT bán trang thiết bị dã ngoại (Spring Boot + Next.js + PostgreSQL +
WebSocket, thanh toán VNPay/COD), đồ án tốt nghiệp nhóm 4 người. Chi tiết:
`docs/01-tong-quan/project-overview.md`.

**Nguồn yêu cầu gốc là báo cáo Phase1_v4** (5 phân hệ, 6 tác nhân, 22 bảng). `docs/` là bản kỹ
thuật hoá của báo cáo; mọi chỗ bổ sung/lệch đều có trong `docs/99-ghi-chu/decisions.md`. Tra use
case → FR → API → route → bảng ở `docs/03-san-pham/use-cases.md`.

Phạm vi chia 3 tier — LUÔN kiểm tra một tính năng thuộc tier nào trước khi code:
- **Tier 1 (bắt buộc)**: auth (OTP email), user, catalog, inventory, cart, order, payment
  (VNPay + COD), admin đơn hàng/sản phẩm/danh mục/tồn kho/người dùng.
- **Tier 2**: combo, voucher + chiến dịch giảm giá, review, trả hàng, favorite, article,
  notification realtime, email, analytics, recommendation, chat hỗ trợ.
- **Tier 3 (chưa làm)**: MoMo, chatbot AI — KHÔNG code nếu chưa được yêu cầu rõ ràng.
- **Không làm**: cho thuê, sổ nhiều địa chỉ, biến thể sản phẩm (`docs/01-tong-quan/scope.md`).

Xem `docs/03-san-pham/features.md` để biết tier, luồng chính và luồng ngoại lệ của từng FR.

## 2. Trước khi code — bắt buộc theo thứ tự
0. Đọc `.agents/progress.md` — xem agent/phiên trước đã làm gì, còn dang dở gì, tránh làm lại.
1. Đọc `docs/` liên quan đến module đang đụng vào (xem mục 4 bên dưới để biết đọc gì).
2. Xác định module bị ảnh hưởng (`backend/src/main/java/vn/campvivo/<module>/`,
   `frontend/src/components/features/<module>/`, `frontend/src/services/<module>Service.ts`).
3. Kiểm tra code đã tồn tại trong module đó — KHÔNG tạo lại chức năng đã có. Code khung hiện có
   có chỗ lệch docs (`docs/99-ghi-chu/known-issues.md` KI-04, KI-05): docs là nguồn đúng.
4. Với thay đổi lớn (>1 module, thêm bảng DB, đổi API contract): tạo plan trong
   `.agents/plans/active/` theo `.agents/plans/_template.md` trước khi code.
5. Dùng skill phù hợp trong `.agents/skills/` — xem mục 5.

## 3. Quy tắc code chung
- Ưu tiên file nhỏ, rõ trách nhiệm. Không tạo abstraction khi chưa cần.
- Không hardcode dữ liệu nghiệp vụ (thời hạn trả hàng, timeout thanh toán, phí ship, OTP...) —
  đọc cấu hình `app.*` (`docs/02-ky-thuat/tech-stack.md`) hoặc DB.
- Tái sử dụng component/service đã có trước khi viết mới.
- Mỗi module backend và mỗi feature frontend độc lập, hạn chế phụ thuộc chéo.
- Không commit secrets (`.env`, khoá VNPay/Cloudinary/SMTP, `JWT_SECRET`).
- Không thêm bảng ngoài 22 bảng, trạng thái, mã lỗi, endpoint, route khi chưa cập nhật docs.
- Chi tiết theo stack: `.agents/rules/general.md`, `.agents/rules/backend.md`,
  `.agents/rules/frontend.md`, `.agents/rules/testing.md`; quy ước code
  `docs/02-ky-thuat/coding-conventions.md`.

## 4. Bản đồ tài liệu (đọc gì cho việc gì)
| Muốn làm... | Đọc trước |
|---|---|
| Hiểu tổng thể dự án, actor, tier | `docs/01-tong-quan/`, `docs/03-san-pham/features.md`, `personas.md` |
| Đối chiếu báo cáo / use case | `docs/03-san-pham/use-cases.md` |
| Quy tắc nghiệp vụ (tồn kho, giá, voucher, trả hàng, OTP, IPN), máy trạng thái, khoá | `docs/02-ky-thuat/database.md` mục 4–6 |
| Thêm/sửa API | `docs/02-ky-thuat/api.md`, `.agents/skills/api-contract/SKILL.md` |
| Đụng vào DB/entity/migration | `docs/02-ky-thuat/database.md`, `.agents/skills/database-design/SKILL.md` |
| Module nào làm gì, event, thanh toán, realtime | `docs/02-ky-thuat/architecture.md` |
| Làm UI mới | `docs/03-san-pham/pages.md`, `user-flows.md`, `ui-guidelines.md`, `.agents/skills/frontend-design/SKILL.md` |
| Auth/phân quyền | `docs/02-ky-thuat/authentication.md` |
| Cấu hình, thư viện | `docs/02-ky-thuat/tech-stack.md`, `coding-conventions.md` |
| Chạy local/Docker/VNPay sandbox | `docs/04-van-hanh/local-development.md`, `docker.md`, `troubleshooting.md` |
| Quyết định đã chốt, vấn đề đã biết | `docs/99-ghi-chu/decisions.md`, `known-issues.md` |

## 5. Skills có sẵn (`.agents/skills/`)
`project-context` · `requirements-analysis` · `implementation-planning` ·
`frontend-design` · `frontend-implementation` · `frontend-review` ·
`backend-development` · `api-contract` · `database-design` ·
`testing-qa` · `code-review` · `debugging` · `documentation-sync` · `git-safety`

Không phải việc nào cũng cần hết các skill. Sửa 1 dòng CSS: chỉ cần `frontend-implementation`.
Thêm 1 luồng nghiệp vụ mới (vd. trả hàng): `requirements-analysis` →
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
Theo 4 module cốt lõi của báo cáo (nhóm xác nhận lại — `docs/01-tong-quan/project-overview.md`):
| Người | Module báo cáo | Module backend | Module frontend |
|---|---|---|---|
| Người 1 | Mua hàng | `cart`, `order` (gồm trả hàng), `payment` | giỏ hàng/thanh toán, đơn cá nhân, admin đơn hàng/trả hàng |
| Người 2 | Quản trị hệ thống | `catalog`, `combo`, `inventory`, , `article`, `analytics` | admin sản phẩm/danh mục/combo/tồn kho/bài viết/dashboard |
| Người 3 | Xác thực và thông tin người dùng | `auth`, `user`, `common`, `support` | layout, `components/ui`, trang auth/hồ sơ, admin user | trang khám phá/tìm kiếm/bài viết, chat realtime
| Người 4 | Khám phá và hỗ trợ + Hệ thống | `review`, `favorite`, , `notification`, `recommendation`, `promotion` | đánh giá, yêu thích, chat, thông báo, voucher/chiến dịch |

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
