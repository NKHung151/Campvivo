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
| CSS | Tailwind CSS (admin, component mới) + CSS hiện có `styles/shop/` (storefront) | |
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
| `app.cors.allowed-origins` | `http://localhost:3000` | |
| `app.frontend-url` | `http://localhost:3000` | Link trong email |

Biến môi trường mẫu: `.env.example` (không commit giá trị thật). Biến `MOMO_*` để trống đến khi làm FR-27.

Lý do các lựa chọn: `docs/99-ghi-chu/decisions.md`.
