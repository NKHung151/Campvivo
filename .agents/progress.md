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

### 2026-10-23 — Người 3 — frontend (F-14: nền gọi API)
- Đã làm: nhánh `feature/frontend-f-14-api-client`. `services/apiClient.ts` (axios, tự gắn token, bóc `{success,data}`,
  `getPage` cho danh sách phân trang, lỗi → `ApiError`, 401 `UNAUTHORIZED` → xoá token + `setUnauthorizedHandler`;
  401 `INVALID_CREDENTIALS` KHÔNG bị coi là hết phiên), `services/serverApi.ts` (`serverGet`/`serverGetPage` cho SSR,
  chỉ endpoint công khai), `services/apiError.ts`, `services/apiConfig.ts`, `lib/authToken.ts` (key `campvivo_token`),
  `lib/errorMessages.ts` (39 mã backend khớp `ErrorCode.java` + `NETWORK_ERROR`/`UNKNOWN_ERROR`, `getErrorMessage`),
  bổ sung kiểu `OrderStatus`, `PaymentMethod`, `PaymentStatus`, `ReturnStatus`, `NotificationType`, `UserProfile`,
  `ApiSuccessBody` vào `types/api/common.ts`. Xoá `lib/api-client.ts` (bản khung chưa ai dùng, thay bằng bản mới).
- Kiểm chứng: lint, tsc, build xanh. Frontend chưa có test runner nên kiểm bằng script tạm ngoài repo (tsx): 16 ca
  với máy chủ giả + 5 ca với backend thật (401/404/mất kết nối/serverGet) đều đạt.
- Còn dang dở / TODO tiếp theo:
  - Handler 401 mặc định chỉ xoá token; chuyển về `/login?redirect=...` do `AuthProvider` đăng ký qua
    `setUnauthorizedHandler` (task nối phiên thật, thay `useAdminSession` giả lập — KI-06).
  - Bảng `errorMessages.ts` chép tay từ `ErrorCode.java`: thêm mã mới ở backend thì thêm ở đây.
  - Chưa có API đăng nhập (N3-03) và `GET /users/me` (N3-05); `catalogService` vẫn đọc mock (N2-04).
  - Tiếp theo: N3-01 (OTP + mail), N3-02, N3-03, N3-05 (`GET /users/me`), rồi task điều hướng theo role + trang `/login`.
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (F-14)

### 2026-10-23 — Người 3 — backend (F-10: security khung)
- Đã làm: nhánh `feature/auth-f-10-security`. `JwtService` (HS256, claim sub/role/tv, dùng `Clock`),
  `JwtAuthenticationFilter` (kiểm `token_version`, `is_active`, role lấy từ DB; principal = userId `Long`),
  `SecurityConfig` (phân quyền theo path như `authentication.md` mục 5, stateless, `@EnableMethodSecurity`),
  `JsonSecurityErrorHandler` (401/403 trả `ApiErrorResponse`), CORS theo `app.cors.allowed-origins`,
  `@CurrentUserId`. Xoá `TemporarySecurityConfig`. Test: `JwtServiceTest` (7), `SecurityIntegrationTest` (13, DB thật).
- Còn dang dở / TODO tiếp theo:
  - Controller của module khác dùng `@CurrentUserId Long userId` (hoặc `@AuthenticationPrincipal Long`, đang dùng ở
    `OrderController`); endpoint chỉ dành Admin phải thêm `@PreAuthorize("hasRole('ADMIN')")`. Endpoint mới phải thêm vào
    ma trận phân quyền N3-10.
  - Swagger (`/swagger-ui.html`, `/v3/api-docs`) đang mở công khai để cả nhóm thử API — đóng/giới hạn khi triển khai.
  - Đường dẫn mới ngoài danh sách trong `SecurityConfig` mặc định cần đăng nhập (401 với khách); endpoint công khai mới
    (GET) phải thêm vào `SecurityConfig`.
  - STOMP `/ws/**` đang permitAll ở tầng HTTP; xác thực JWT khi CONNECT làm ở N3-08.
  - Tiếp theo: F-14 (apiClient FE), F-12 (seed), F-11 (cả nhóm), rồi N3-01 (OTP).
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (F-10)

### 2026-10-23 — Người 3 — backend (F-05: bảng users)
- Đã làm: nhánh `feature/user-f-05`. `V1__users.sql` (16 cột theo `database.md` 3.1, CHECK `role`/`otp_purpose`),
  entity `User` (email tự chuẩn hoá chữ thường), `Role`, `OtpPurpose`, `UserRepository.findByEmail`. Xoá
  `V1__init_tier1.sql`, `V2__tier2.sql` (placeholder, D-14). `UserRepositoryTest` (5 test, PostgreSQL thật,
  `ddl-auto=validate`). Nâng Testcontainers 1.19.8 → 1.21.4 (bản cũ không kết nối được Docker Desktop 29, API ≥ 1.44).
  Docs: `database.md` mục 1, D-14, KI-04.
- Còn dang dở / TODO tiếp theo:
  - Ai pull nhánh này: chạy `mvn clean` một lần (target/ còn migration cũ gây "more than one migration with version 1")
    và reset DB local (`docker compose down -v` rồi `up -d`) vì lịch sử Flyway đổi.
  - `ddl-auto=validate` vẫn lỗi cho đến khi F-06 (N2) và F-08 (N1) tạo các bảng còn lại; tạm `ddl-auto: none` ở file dev.
  - Tiếp theo: F-10 (security, xoá `TemporarySecurityConfig`), F-14 (apiClient FE), F-12 (seed), F-11 (cả nhóm).
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (F-05)

### 2026-10-22 — Người 3 — backend (nền móng: F-01, F-02, F-03)
- Đã làm: nhánh `feature/common-foundation`.
  - F-01: thêm dependency jjwt, websocket, thymeleaf, OWASP sanitizer, cloudinary, springdoc 2.6.0
    (khớp Spring Boot 3.3), greenmail (test). `TemporarySecurityConfig` mở Swagger — XOÁ ở F-10.
  - F-02: `ErrorCode` (39 mã, khớp `api.md` mục 2, có test đối chiếu docs), `BusinessException(ErrorCode, details)`,
    `GlobalExceptionHandler` (validate → `fieldErrors`, 401/403, 500 không lộ chi tiết), `PageResponse`, `Money`,
    `SlugUtil`, `HtmlSanitizer`, bean `Clock` (Asia/Ho_Chi_Minh), `RequestIdFilter` (X-Request-Id → MDC/log).
    Thêm mã `INTERNAL_ERROR` (500) vào `api.md`.
  - F-03: `AppProperties` (`app.*`, secret JWT bắt buộc ≥ 32 ký tự), `application.yml`
    (`ddl-auto=validate`, `open-in-view=false`), `.env.example` thêm `NEXT_PUBLIC_WS_URL`.
- Còn dang dở / TODO tiếp theo:
  - `ddl-auto=validate` khiến app không khởi động đến khi F-05 xong (entity lệch migration placeholder, KI-04);
    tạm dùng `ddl-auto: none` trong `application-dev.yml` local.
  - Máy Windows có JVM timezone `Asia/Saigon` → PostgreSQL từ chối; chạy với `JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Ho_Chi_Minh`.
  - `OrderServiceIT` hiện không chạy trong `mvn test` (tên `*IT`, chưa có failsafe) — nhờ N4 (F-04) xử lý.
  - `BusinessException(String, String, HttpStatus)` là `@Deprecated`; `ProductService`, `OutOfStockException`
    cần chuyển sang `ErrorCode` (N2, N1). DTO phải ghi message validate bằng tiếng Việt.
  - Test tích hợp sau này phải cấp `app.jwt.secret` (CI chưa có `JWT_SECRET`).
  - Tiếp theo: F-05 (bảng users), F-10 (security), F-14 (apiClient FE), F-12 (seed).
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (F-01 → F-03)

### 2026-10-17 — Nguyen Khai Hung — frontend (khung admin)
- Đã làm: tách root layout `app/(store)` (storefront, CSS `styles/shop` chuyển lên layout này) và `app/(admin)`
  (Ant Design 5, locale vi_VN); `AdminShell` với sidebar theo role (`lib/admin/nav.ts`), `RequireRole`, trang
  "đang xây dựng" cho mục chưa làm, 404 riêng cho admin và storefront (catch-all); `types/api/common.ts`,
  `types/api/catalog.ts`. Docs: D-18, architecture, tech-stack, ui-guidelines, pages, api (mục 4.11), KI-06.
- Kiểm tra: lint, tsc, build xanh; chạy thử storefront + admin (ADMIN/EMPLOYEE/CUSTOMER) và chụp màn hình.
- TODO: phiên admin đang giả lập (KI-06) — nối auth thật khi có F-14/N3-03; tiếp theo là BE catalog
  (migration V2__catalog, API danh mục/sản phẩm) và trang /admin/categories, /admin/products.
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (N2-17, N2-01, N2-02, N2-18 → N2-21)

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

