# Tech Stack

| Thành phần | Công nghệ | Ghi chú |
|---|---|---|
| Backend | Java + Spring Boot (Spring Web, Spring Data JPA, Spring Security) | REST API |
| Frontend | Next.js (App Router) + React + TypeScript | SSR cho trang sản phẩm, CSR cho giỏ hàng/checkout |
| Database | PostgreSQL | Flyway cho migration |
| Auth | JWT (stateless) + BCrypt | Không dùng session server |
| Thanh toán | MoMo / VNPay (sandbox) + COD | Webhook idempotent qua `transaction_id` |
| Lưu trữ ảnh | Cloudinary (hoặc tương đương nếu không có tài khoản) | |
| Email | SMTP (Spring Boot Mail Starter) | Log vào bảng `email_notifications` |
| CI | GitHub Actions | Build + lint khi tạo PR vào `dev`/`main` |
| Test backend | JUnit + Testcontainers (PostgreSQL thật cho test transaction) | |
| Biểu đồ | Recharts (frontend) | Dashboard doanh thu |

Lý do chọn stack này và các lựa chọn đã cân nhắc nhưng không chọn: xem
`docs/99-ghi-chu/decisions.md`.
