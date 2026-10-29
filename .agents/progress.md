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

### 2026-10-29 — Người 3 — frontend (sửa `next build` lỗi NETWORK_ERROR trên CI)
- Đã làm: nhánh `fix/frontend-server-api-dynamic-build` (tách từ `dev`). Nguyên nhân (đã chứng minh bằng cách in lỗi gốc): `services/serverApi.ts`
  (F-14) bọc `fetch` no-store trong `catch` đổi MỌI lỗi thành `NETWORK_ERROR`, nuốt `DYNAMIC_SERVER_USAGE` mà Next ném lúc prerender → `next build`
  hỏng ở `/`, `/brands`, `/products` khi không có backend (CI) và cả khi có backend. Sửa: để lỗi điều khiển luồng của Next (`digest` =
  `DYNAMIC_SERVER_USAGE` / `BAILOUT_TO_CLIENT_SIDE_RENDERING`) đi tiếp. Kiểm chứng: `npm run build` không có backend (như CI) exit 0, các trang
  catalog chuyển sang render động; chạy `next start` với backend tắt thì lỗi mạng thật vẫn được ghi nhận là `NETWORK_ERROR`. Trước khi sửa, `dev` sạch
  build exit 1. Docs: `lessons-learned.md`.
- Còn dang dở / TODO tiếp theo:
  - Chạy thật với backend tắt, `/brands` trả HTTP 500 (trang catalog không bắt `ApiError`); roadmap N2-04 yêu cầu "tắt BE → trang báo lỗi thân thiện".
    Việc này thuộc catalog (Người 2), không nằm trong PR sửa này.
  - Không xem được CI từ máy này; kết quả CI sau khi sửa cần người mở PR xác nhận.
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (F-14, N2-04)

### 2026-10-26 — Người 3 — backend (N3-02: đăng ký, xác thực email, gửi lại mã)
- Đã làm: nhánh `feature/auth-n3-02-register` (XẾP CHỒNG lên `feature/auth-n3-01-otp`: PR 1 phải merge trước). `POST /auth/register` (201 `{email}`;
  email chưa xác thực đăng ký lại → ghi đè mật khẩu/họ tên + mã mới; gửi lại < 60 giây → `OTP_RESEND_TOO_SOON`, rollback không ghi đè;
  gửi mail lỗi → `EMAIL_SEND_FAILED`, không tạo user; email đã xác thực → `EMAIL_ALREADY_EXISTS`; mật khẩu > 72 byte → `VALIDATION_ERROR`),
  `POST /auth/verify-email` (trả `{accessToken, user}`; email lạ và mã sai cùng `INVALID_OTP`, có băm giả chống dò qua độ trễ; tài khoản
  khoá → `ACCOUNT_DISABLED`), `POST /auth/resend-code` (200 im lặng với email lạ / tài khoản khoá / đã xác thực). `RegistrationService`,
  DTO, `UserRepository.markEmailVerified`, giao diện `CartService` + `StubCartService` (module cart, TẠM — bảng `carts` chưa có). Test:
  `RegistrationIntegrationTest` (25, PostgreSQL thật + GreenMail + đồng hồ chỉnh tay, gồm đăng ký song song cùng email). Kiểm tra "đột biến":
  cố ý cho đăng ký đè tài khoản đã xác thực và bỏ kiểm tra tài khoản khoá → đúng 2 test đỏ. Chạy thật đầu-cuối trên ứng dụng + Mailpit:
  đăng ký → đọc mã trong thư → xác thực → `/users/me`. Docs: `api.md` (hành vi đã hiện thực), `local-development.md` (cách thử bằng PowerShell).
- Còn dang dở / TODO tiếp theo:
  - Người 1: hiện thực `CartService` thật (tạo dòng `carts`) rồi XOÁ `StubCartService` (có TODO trong code).
  - RỦI RO đã biết, theo đúng FR-01: đăng ký lại email CHƯA xác thực ghi đè mật khẩu — kẻ biết email người khác có thể ghi đè mật khẩu của tài
    khoản đang chờ xác thực; nếu người thật sau đó xác thực bằng mã trong hộp thư của họ thì tài khoản mang mật khẩu của kẻ kia.
    Cân nhắc (cần nhóm quyết định): không ghi đè mật khẩu khi đăng ký lại, hoặc yêu cầu mật khẩu mới chỉ có hiệu lực sau khi xác thực.
  - Chưa có giới hạn theo IP (chỉ chống gửi lại 60 giây theo từng email) → có thể bị lợi dụng spam thư tới email người khác; nên cân nhắc rate limit
    trước khi triển khai thật. Độ trễ phản hồi của `register`/`resend-code` khác nhau giữa email có và không có (gửi mail) → vẫn có thể dò email qua thời gian.
  - Đăng ký song song cùng email: yêu cầu thứ hai nhận `EMAIL_ALREADY_EXISTS` dù người dùng chỉ bấm hai lần.
  - Frontend (PR 3): trang `/register`, `/verify-email` (đếm ngược 60 giây), liên kết "Tạo tài khoản"; gọi 3 endpoint với `{ skipAuth: true }`.
  - N3-04 (quên mật khẩu) dùng lại `OtpService`/`MailService`/`resend-code` (đã hỗ trợ purpose RESET_PASSWORD).
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (N3-02)

### 2026-10-26 — Người 3 — backend (N3-01: OTP + gửi email)
- Đã làm: nhánh `feature/auth-n3-01-otp`. `OtpService` (BR-11: mã 6 số `SecureRandom`, chỉ lưu BCrypt, hạn `app.otp.ttl`, tối đa
  `app.otp.max-attempts` lần thử, gửi lại cách ≥ `app.otp.resend-interval`; `issue(User, purpose)` ghi OTP vào entity, `verify(userId,
  purpose, code)` chạy giao dịch RIÊNG REQUIRES_NEW: lượt đoán không bị mất khi người gọi rollback, tăng lượt bằng UPDATE nguyên tử nên
  đoán song song không vượt giới hạn, OTP dùng một lần), `MailService` (gửi đồng bộ, mẫu Thymeleaf `templates/mail/otp.html`, tiêu đề theo
  mục đích, escape HTML, lỗi → `EMAIL_SEND_FAILED`, không log mã), cấu hình `spring.mail.*` (mặc định Mailpit `localhost:1025`, timeout
  5/10 giây), `management.health.mail.enabled=false`, `app.mail.from/from-name`, thêm **Mailpit** vào `docker-compose.yml` (đọc thư ở
  http://localhost:8025). Test: `OtpServiceTest` (14, PostgreSQL thật: gồm đoán song song, rollback người gọi, hết hạn đúng biên),
  `MailServiceTest` (5, GreenMail). Kiểm tra "đột biến": cố ý làm yếu giới hạn lượt đoán và giao dịch riêng → đúng 4 test đỏ.
  Đã gửi thử thư thật tới Mailpit và xem lại nội dung. Docs: `tech-stack.md`, `docker.md`, `local-development.md`,
  `troubleshooting.md`, `.env.example` (SMTP_* để comment: biến rỗng ghi đè mặc định).
- Còn dang dở / TODO tiếp theo:
  - N3-02 (PR kế tiếp): `register`, `verify-email`, `resend-code`. Người gọi `issue()` phải lưu user trong giao dịch của mình và gửi mail
    SAU `issue()` (lỗi mail → rollback, không tạo user/OTP). `verify()` đừng gọi khi giao dịch của người gọi đang giữ thay đổi chưa commit
    trên chính dòng user đó. Tạo giỏ hàng rỗng khi đăng ký: bảng `carts` chưa có → dùng stub `CartService.createForUser` (F-11), ghi TODO.
  - `otp_last_sent_at` được GIỮ khi OTP dùng xong (vẫn giới hạn tần suất gửi lại); BR-11 chỉ nói "xoá các cột OTP".
  - Mặc định SMTP `localhost:1025` ở môi trường thật sẽ gây `EMAIL_SEND_FAILED` nếu quên đặt `SMTP_*`.
  - Test OTP dùng `MutableTestClock` lồng riêng trong test; khi F-04 (Người 4) có `MutableClock` chung thì gom lại.
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (N3-01)

### 2026-10-24 — Người 3 — docs/config (hướng dẫn chạy local để đăng nhập được)
- Đã làm: nhánh `docs/local-dev-setup`. `application-dev.yml.example` đổi mặc định `ddl-auto: validate` → `none` (kèm chú thích
  lý do và khi nào đổi lại), vì `validate` làm backend báo `missing table [...]` đến khi F-06 → F-09 merge và ai copy `.example`
  theo hướng dẫn cũ đều gặp lỗi. `local-development.md` thêm mục "Thiết lập lần đầu / sau khi pull code mới" (pull `dev`, reset DB
  `docker compose down -v`, tạo `application-dev.yml`, chạy backend kèm múi giờ, chèn 3 tài khoản mẫu, chạy frontend cổng 3000).
- Còn dang dở / TODO tiếp theo:
  - Khi F-06 → F-09 đã vào `dev`: đổi `.example` về `validate` (hoặc xoá dòng) và cập nhật lại mục trên.
  - Làm F-12 (seed) thì bỏ bước chèn tay tài khoản mẫu.

### 2026-10-24 — Người 3 — frontend (đăng nhập thật + điều hướng theo role)
- Đã làm: nhánh `feature/frontend-auth-login-redirect`. `AuthProvider`/`useAuth` (khôi phục phiên bằng `GET /users/me`,
  trạng thái `loading|authenticated|unauthenticated|error`, đồng bộ giữa các tab, nối `setUnauthorizedHandler`), `authService`,
  trang `/login` (`LoginForm`, `components/ui/Button`, `Input` — hai component dùng chung đầu tiên), `lib/auth/redirect.ts`
  (`safeRedirectPath` chặn open redirect, `canAccessPath`, `landingFor`, `resolveLanding`, `loginHref`). Điều hướng: có `?redirect`
  hợp lệ và role được vào → đó; không thì ADMIN `/admin`, EMPLOYEE `/admin/orders`, CUSTOMER `/`. Admin: thay `useAdminSession`
  giả lập bằng phiên thật (xoá ô "Dev", bật Đăng xuất, chưa đăng nhập → `/login?redirect=`, lỗi mạng → "Thử lại").
  Storefront: bỏ `LoginPopup` OTP giả và login giả trong `ShopProvider` (`user` lấy từ phiên thật, `openLogin` → `/login`).
  Sửa `apiClient`: thêm `skipAuth` (endpoint xác thực công khai không gắn token — token cũ còn sót chặn đăng nhập).
  Docs: `pages.md`, `authentication.md`, `known-issues.md` (KI-06 đã xử lý), `lessons-learned.md`; bỏ `NEXT_PUBLIC_ADMIN_DEV_ROLE`.
- Kiểm chứng: lint, tsc, build xanh; script tạm ngoài repo: 12 nhóm kiểm tra hàm điều hướng (18 dạng URL độc hại bị chặn),
  13 ca luồng thật với backend (3 tài khoản mẫu: đăng nhập, `/users/me`, đăng xuất, sai mật khẩu, validate, `?redirect`),
  2 ca hồi quy token cũ; server Next thật phục vụ `/login`, `/admin`, `/`, `/account`, `/cart` (200); CSS storefront không lẫn vào admin.
- Còn dang dở / TODO tiếp theo:
  - CHƯA kiểm tra trên trình duyệt thật (bố cục `/login`, mobile 375px, thao tác click) — người dùng kiểm theo checklist.
  - `EMAIL_NOT_VERIFIED` chỉ hiện thông báo; tự gửi lại mã + chuyển `/verify-email` làm ở N3-02. Chưa có liên kết "Tạo tài khoản",
    "Quên mật khẩu?" (trang chưa có). Nút "Tạo tài khoản" ở popover Header hiện cũng dẫn tới `/login`.
  - `/api/admin/**` ở backend vẫn cho EMPLOYEE vào mọi đường dẫn admin; frontend đã chặn menu theo role nhưng backend phải chặn thật.
  - Endpoint xác thực công khai mới (register, verify-email, resend-code, forgot/reset-password) phải gọi với `{ skipAuth: true }`.
  - Giỏ hàng/đơn/yêu thích của storefront vẫn là mock (Người 1); `user` giả cũ trong localStorage bị bỏ qua.
  - Chuyển giữa storefront và admin tải lại toàn trang nên phiên được khôi phục lại bằng `GET /users/me` mỗi lần.
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (nối frontend auth, tuần 7)

### 2026-10-23 — Người 3 — backend (N3-03: đăng nhập/đăng xuất + GET /users/me)
- Đã làm: nhánh `feature/auth-n3-03-login`. `POST /auth/login` (sai email và sai mật khẩu trả CÙNG `INVALID_CREDENTIALS`,
  BCrypt giả cho email không tồn tại để chống dò email qua độ trễ; chỉ khi mật khẩu đúng mới báo `EMAIL_NOT_VERIFIED` /
  `ACCOUNT_DISABLED`), `POST /auth/logout` (tăng `token_version` bằng UPDATE nguyên tử), `GET /users/me`
  (`UserService`, `UserController`), bean `PasswordEncoder` BCrypt strength 10. Test `AuthLoginIntegrationTest`
  (14, PostgreSQL thật); sửa `SecurityIntegrationTest` (đường dẫn thử đổi sang `/api/notifications` vì `/users/me` đã có controller).
  Kiểm tay trên app thật với 3 tài khoản mẫu: đúng role, đúng quyền, logout làm token cũ 401.
  Docs: `local-development.md` (chèn tay tài khoản mẫu), D-19, `troubleshooting.md`, `lessons-learned.md`.
- Còn dang dở / TODO tiếp theo:
  - Làm N3-03 TRƯỚC N3-01/N3-02 (roadmap ghi phụ thuộc N3-02 nhưng không phụ thuộc kỹ thuật; test tạo user bằng repository).
    Ưu tiên của buổi: điều hướng theo role cho trang admin/staff. N3-01 (OTP + mail), N3-02, N3-04 làm sau.
  - N3-05 mới có phần `GET /users/me`; chưa có `PUT /users/me` và `PUT /users/me/password` → KHÔNG tick N3-05.
  - Chưa có giới hạn số lần đăng nhập sai (docs không yêu cầu); cân nhắc rate limit trước khi triển khai thật.
  - Tài khoản mẫu chèn tay (D-19) — F-12 chính thức làm sau khi F-06 → F-09 merge.
  - `UserController` import `CurrentUserId` của module `auth` (annotation dùng chung); có thể chuyển sang `common` nếu cần.
  - Tiếp theo: PR frontend (AuthProvider, `/login`, `landingFor(role, redirect)`, bảo vệ layout admin; thay `useAdminSession` — KI-06).
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (N3-03)

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

