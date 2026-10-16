# Progress Log (append-only)

Ghi lại NGẮN GỌN sau mỗi phiên làm việc đáng kể (xong 1 feature, 1 bugfix lớn, 1 quyết định
kỹ thuật). Agent phiên sau đọc file này TRƯỚC `project-context` để biết trạng thái gần nhất,
tránh làm lại hoặc đi lệch hướng đã có.

KHÔNG sửa/xoá dòng cũ — chỉ thêm dòng mới lên đầu danh sách bên dưới. Lịch sử đầy đủ hơn nằm
ở Git log; file này chỉ là tóm tắt nhanh cho agent.

## Định dạng mỗi mục
```
### <ngày> — <người/agent> — <module>
- Đã làm:
- Còn dang dở / TODO tiếp theo:
- Plan liên quan (nếu có): .agents/plans/active/<ten>.md
```

---

### 2026-10-17 — Nguyen Khai Hung — docs (đồng bộ theo báo cáo Phase1_v4)
- Đã làm: viết lại bộ docs theo báo cáo Phase1_v4 (5 phân hệ, 6 tác nhân, 22 bảng):
  - `decisions.md` D-00…D-17 (bỏ cho thuê, không sổ địa chỉ, combo tồn kho riêng, giỏ không chốt giá,
    SupportMessage thêm `is_from_staff`, VNPay + COD, OTP email, `token_version`, schema PostgreSQL 22 bảng…).
  - `features.md` (FR-01…FR-28 + luồng ngoại lệ), `use-cases.md` mới (use case → FR → API → route → bảng).
  - `database.md` (đặc tả từng cột, BR-1…BR-14, máy trạng thái, thứ tự khoá), `api.md` (toàn bộ
    endpoint, mã lỗi, kiểu TS), `authentication.md`, `architecture.md`, `tech-stack.md`,
    `coding-conventions.md`, `pages.md`, `user-flows.md`, `01-tong-quan/*`, vận hành, known-issues.
  - `.agents/rules`, skills, `AGENTS.md`, README, PR template.
- Còn dang dở / TODO tiếp theo:
  - Nhóm xác nhận: giữ COD, đăng xuất bằng `token_version`, phân công 4 người, phí ship, số ngày trả hàng.
  - Sửa báo cáo Phase1_v5 cho khớp docs (cột bổ sung ở `database.md` mục 2, use case "Process return
    request", chat với nhân viên thay chatbot AI…).
  - Viết migration đầy đủ 22 bảng thay V1/V2 placeholder: `.agents/plans/active/backend-schema-v1.md`.
  - Code backend hiện có lệch docs (KI-04); frontend mock lệch schema (KI-05).
- Plan liên quan: `.agents/plans/active/backend-schema-v1.md`, `.agents/plans/active/frontend-shop-ui.md`

### 2026-10-08 — Nguyen Khai Hung — frontend (ẩn nội dung mock của bên thứ ba)
- Đã làm:
  - `src/services/mockContent.ts` (`cleanMock`, gọi trong `readJson` của `catalogService`) xử lý chữ nhắc tên và hotline của cửa hàng tham khảo trong dữ liệu mock:
    - tiêu đề: bỏ nhãn;
    - đoạn ngắn: ẩn;
    - nội dung dài (bài viết, mô tả sản phẩm, chính sách): thay bằng ô "Nội dung đang được cập nhật".
  - Không sửa file mock. Dữ liệu do nhóm tự viết sẽ không bị ảnh hưởng.
  - `/guides` có phần đầu trang Campvivology riêng.
  - Bỏ banner showroom (ảnh có in chữ thương hiệu) ở trang chi tiết bài viết.
- TODO: thay nội dung, ảnh mock bằng dữ liệu của nhóm. Bộ lọc không xử lý được chữ in trong ảnh (banner, ảnh sản phẩm).

### 2026-10-08 — Nguyen Khai Hung — frontend (New arrivals + Outdoor Journal)
- Đã làm:
  - New arrivals: `/categories/san-pham-moi`, có banner (trường `banner` của Listing) và 119 sản phẩm.
  - Outdoor Journal: `/journal` (phân trang, 10 danh mục, 102 bài chi tiết trong `src/mocks/data/journal/`). CSS riêng `styles/shop/journal.css` (`.pg-journal`). Tái sử dụng `ArticleList` / `ArticleDetail` (thêm tham số `hrefFor`, `breadcrumbs`, `wrapperClass`).
  - Link "New arrivals" và "Outdoor Journal" ở header và footer đã nối vào các trang mới.
- Còn dang dở: mới có 5 trang danh sách journal (45 bài) và trang đầu của mỗi danh mục. Bộ lọc "15 đỉnh núi" và "Chủ đề HOT" mới chỉ bật/tắt được, chưa lọc.

### 2026-10-08 — Nguyen Khai Hung — frontend (storefront UI)
- Đã làm: đưa giao diện cửa hàng desktop vào `frontend/` trên nhánh `feature/frontend-ui-shop` (tách từ `dev`). Gồm:
  - các trang: trang chủ, tìm kiếm, danh mục, chi tiết sản phẩm, giỏ hàng + thanh toán mock, tài khoản, thương hiệu, blog, trang chính sách, khuyến mãi;
  - route theo `docs/03-san-pham/pages.md` (đã bổ sung route mới vào đó);
  - dữ liệu mock ở `src/mocks/data`, đọc qua `src/services/catalogService.ts`;
  - giỏ hàng, đăng nhập OTP giả, đơn hàng lưu localStorage (`components/features/shop/ShopProvider.tsx`).
- Kiểm tra: lint, typecheck và build (Next 14.2.5) đều qua; đã chạy thử từng route và luồng mua hàng.
- Còn dang dở / TODO tiếp theo:
  - Nối API backend: thay `catalogService` và `ShopProvider` bằng `services/<module>Service.ts`.
  - Giao diện mobile.
  - Trang `/login`, `/register` riêng.
  - Thay nội dung, ảnh mẫu bằng dữ liệu thật của Campvivo. Ảnh mẫu đang tải từ host bên ngoài.
  - Các link `data-dead` (liên hệ, hỏi đáp, cộng đồng, mạng xã hội) chưa có trang.
- Plan liên quan: `.agents/plans/active/frontend-shop-ui.md`

