# Tech Stack

| Thành phần | Công nghệ | Ghi chú |
|---|---|---|
| Backend | Java 17 + Spring Boot 3.5.16 (Web, Data JPA, Security, Validation, WebSocket, Mail) | REST API `/api` + STOMP `/ws`. Swagger: springdoc 2.8.17 (D-27) |
| Frontend | Next.js 14 (App Router) + React 18 + TypeScript | SSR trang public, CSR giỏ hàng/checkout/tài khoản/admin |
| HTTP client FE | axios (`services/apiClient.ts`) | |
| Realtime FE | `@stomp/stompjs` + `sockjs-client` | Thông báo, chat hỗ trợ |
| Database | PostgreSQL 16 + pgvector (image `pgvector/pgvector:pg16`) | Flyway cho migration; pgvector cho chatbot (D-27) |
| Auth | JWT HS256 (jjwt) + BCrypt | Stateless, `token_version` để thu hồi (D-11) |
| OTP / Email | Spring Boot Mail (SMTP) + Thymeleaf template | OTP gửi đồng bộ; email nghiệp vụ `@Async` sau commit |
| Thanh toán | VNPay sandbox + COD | IPN idempotent; MoMo giai đoạn sau qua `PaymentGateway` (D-08) |
| Lưu trữ ảnh | Cloudinary | Upload qua backend (`/admin/uploads/images`) |
| Sanitize HTML | OWASP Java HTML Sanitizer | Mô tả sản phẩm, nội dung bài viết |
| Biểu đồ | Recharts | Dashboard |
| Giao diện admin | Ant Design 5 (`antd` 5.29.3, `@ant-design/icons` 5.6.1, `@ant-design/nextjs-registry` 1.3.0) | Chỉ nạp trong root layout `(admin)` (D-18) |
| Soạn thảo nội dung (admin) | TipTap 3.31.3 (`@tiptap/react`, `@tiptap/starter-kit`, `@tiptap/extension-image`, `@tiptap/extensions`, `@tiptap/pm`, `@tiptap/core`) | Mô tả sản phẩm (sau này: nội dung bài viết). Xuất HTML; ảnh chèn vào tải lên Cloudinary; backend vẫn làm sạch bằng `HtmlSanitizer` |
| CSS storefront | CSS thuần `styles/shop/*` (chỉ nạp trong root layout `(store)`) | Không SCSS; Tailwind có trong package.json nhưng chưa dùng |
| Chatbot AI (FR-28) | `chatbot-service`: Spring Boot 3.5.16 + Spring AI 1.1.8 (OpenAI chat + `text-embedding-3-small`, Tika/PDF document reader) + pgvector | Service riêng cổng 8090, dùng chung DB (D-28). Key OpenAI: env `OPENAI_API_KEY` |
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

### Cấu hình `chatbot-service` (`chatbot-service/src/main/resources/application.yml`, D-28)
| Key | Mặc định | Ý nghĩa |
|---|---|---|
| `spring.ai.openai.api-key` | env `OPENAI_API_KEY` | Bắt buộc khi chạy thật; test dùng model giả |
| `spring.ai.openai.chat.options.model` | env `OPENAI_CHAT_MODEL`, mặc định `gpt-4.1-mini` | Đổi model không cần sửa code |
| `spring.ai.openai.embedding.options.model` | `text-embedding-3-small` | 1536 chiều — đổi model phải đổi cột `vector(1536)` và nạp lại |
| `app.jwt.secret` / `app.cors.allowed-origins` | env `JWT_SECRET` / `http://localhost:3000` | Giống backend |
| `app.chatbot.message-max-length` | `1000` | Ký tự mỗi tin của khách |
| `app.chatbot.history-size` | `20` | Số tin gần nhất gửi kèm cho model |
| `app.chatbot.max-tool-calls` | `5` | Số lượt gọi tool tối đa mỗi câu hỏi |
| `app.chatbot.rate-limit.per-minute` / `per-day` | `20` / `200` | Mỗi khách |
| `app.chatbot.knowledge.max-file-size` | `10MB` | |
| `app.chatbot.knowledge.chunk-size` | `600` | Token mỗi đoạn |
| `app.chatbot.knowledge.top-k` / `similarity-threshold` | `5` / `0.3` | Truy hồi tài liệu (độ giống cosine; xem điểm ở "Hỏi thử" để chỉnh) |
| `app.chatbot.embedding.enabled` / `refresh-interval` | `true` / `10m` | Job embed lại sản phẩm/combo đã đổi (chạy khi khởi động rồi lặp theo chu kỳ) |

Biến môi trường mẫu: `.env.example` (không commit giá trị thật). Biến `MOMO_*` để trống đến khi làm FR-27.

Lý do các lựa chọn: `docs/99-ghi-chu/decisions.md`.
