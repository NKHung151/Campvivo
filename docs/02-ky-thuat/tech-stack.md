# Tech Stack

| Thành phần | Công nghệ | Ghi chú |
|---|---|---|
| Backend | Java 17 + Spring Boot 3 (Web, Data JPA, Security, Validation, WebSocket, Mail) | REST API `/api` + STOMP `/ws` |
| Frontend | Next.js 14 (App Router) + React 18 + TypeScript | SSR trang public, CSR giỏ hàng/checkout/tài khoản/admin |
| HTTP client FE | axios (`services/apiClient.ts`) | |
| Realtime FE | `@stomp/stompjs` + `sockjs-client` | Thông báo, chat hỗ trợ |
| Database | PostgreSQL 15+ | Flyway cho migration |
| Auth | JWT HS256 (jjwt) + BCrypt | Stateless, `token_version` để thu hồi (D-11) |
| OTP / Email | Spring Boot Mail (SMTP) + Thymeleaf template | OTP gửi đồng bộ; email nghiệp vụ `@Async` sau commit |
| Thanh toán | VNPay sandbox + COD | IPN idempotent; MoMo giai đoạn sau qua `PaymentGateway` (D-08) |
| Lưu trữ ảnh | Cloudinary | Upload qua backend (`/admin/uploads/images`) |
| Sanitize HTML | OWASP Java HTML Sanitizer | Mô tả sản phẩm, nội dung bài viết |
| Biểu đồ | Recharts | Dashboard |
| Giao diện admin | Ant Design 5 (`antd` 5.29.3, `@ant-design/icons` 5.6.1, `@ant-design/nextjs-registry` 1.3.0) | Chỉ nạp trong root layout `(admin)` (D-18) |
| Soạn thảo nội dung (admin) | TipTap 3.31.3 (`@tiptap/react`, `@tiptap/starter-kit`, `@tiptap/extension-image`, `@tiptap/extensions`, `@tiptap/pm`, `@tiptap/core`) | Mô tả sản phẩm (sau này: nội dung bài viết). Xuất HTML; ảnh chèn vào tải lên Cloudinary; backend vẫn làm sạch bằng `HtmlSanitizer` |
| CSS storefront | CSS thuần `styles/shop/*` (chỉ nạp trong root layout `(store)`) | Không SCSS; Tailwind có trong package.json nhưng chưa dùng |
| CI | GitHub Actions | Build + lint khi tạo PR vào `dev`/`main` |
| Test backend | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL thật) | Bắt buộc cho transaction/locking |
| Test frontend | ESLint + `next build`; e2e (Playwright) cho luồng mua hàng nếu kịp | |

## Cấu hình (`application.yml`, biến môi trường cho secret)
| Key | Mặc định | Ý nghĩa |
|---|---|---|
| `app.jwt.secret` / `app.jwt.ttl` | env `JWT_SECRET` / `24h` | |
| `app.otp.ttl` / `app.otp.max-attempts` / `app.otp.resend-interval` | `10m` / `5` / `60s` | BR-11 |
| `app.order.payment-timeout` | `15m` | BR-3 |
| `app.return.window-days` | `7` | BR-9 |
| `app.shipping.fee` / `app.shipping.free-threshold` | `30000` / `1000000` | D-17 |
| `app.inventory.low-stock-threshold` | `5` | FR-08 |
| `app.vnpay.tmn-code` / `hash-secret` / `pay-url` / `return-url` / `ipn-url` | env `VNPAY_*` | |
| `app.mail.from` / `app.mail.from-name` | `no-reply@campvivo.local` / `Campvivo` | Người gửi email OTP/thông báo |
| `spring.mail.*` (env `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`) | `localhost`, `1025`, rỗng, rỗng, `false`, `false` | Mặc định trỏ Mailpit (docker compose). Gmail: `smtp.gmail.com`, `587`, `SMTP_AUTH` và `SMTP_STARTTLS` = `true`. Timeout kết nối 5 giây, đọc/ghi 10 giây |
| `app.cors.allowed-origins` | `http://localhost:3000` | |
| `app.frontend-url` | `http://localhost:3000` | Link trong email |

Biến môi trường mẫu: `.env.example` (không commit giá trị thật). Biến `MOMO_*` để trống đến khi làm FR-27.

Lý do các lựa chọn: `docs/99-ghi-chu/decisions.md`.
