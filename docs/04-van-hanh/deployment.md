# Deployment

> Điền khi nhóm chốt nền tảng deploy (vd. Render/Railway/VPS...). Tối thiểu cần ghi rõ:

- Nơi deploy backend, frontend, database.
- Biến môi trường cần cấu hình ở production: DB connection, `JWT_SECRET`, `VNPAY_*` (+ IPN URL
  public khai báo với VNPay), SMTP, `CLOUDINARY_URL`, `app.cors.allowed-origins`, `app.frontend-url`.
  (`MOMO_*` khi làm FR-27.) Danh sách key: `docs/02-ky-thuat/tech-stack.md`.
- Reverse proxy phải hỗ trợ WebSocket (upgrade header) cho `/ws`.
- Quy trình deploy: tự động qua CI khi merge vào `main`, hay deploy thủ công.
- Domain/URL demo dùng khi báo cáo đồ án. Không seed tài khoản mẫu dev ở môi trường demo công khai.
