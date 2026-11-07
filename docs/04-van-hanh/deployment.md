# Deployment

> Điền khi nhóm chốt nền tảng deploy (vd. Render/Railway/VPS...). Tối thiểu cần ghi rõ:

- Nơi deploy backend, frontend, database.
- Biến môi trường cần cấu hình ở production (backend; KHÔNG đặt key nào có tiền tố `NEXT_PUBLIC_`): DB connection, `JWT_SECRET`,
  `VNP_TMN_CODE`, `VNP_HASH_SECRET`, `VNP_URL` (production VNPay khi chạy thật), `VNP_RETURN_URL=https://<frontend>/payment/vnpay-return`,
  `GHN_*` (production: `GHN_BASE_URL=https://online-gateway.ghn.vn` + token/shop production), SMTP, `CLOUDINARY_*`,
  `APP_CORS_ALLOWED_ORIGINS=https://<frontend>`, `APP_FRONTEND_URL`, `PORT` (nếu nền tảng cấp). Frontend: `NEXT_PUBLIC_API_URL`,
  `NEXT_PUBLIC_WS_URL`. (`MOMO_*` khi làm FR-27.) Danh sách key: `docs/02-ky-thuat/tech-stack.md`.
- VNPay: khai báo IPN URL `https://<backend>/api/vnpay/ipn` ở cổng merchant (đường xử lý chính khi deploy, D-28).
- Backend sau reverse proxy: đã bật `server.forward-headers-strategy=framework`; proxy phải gửi `X-Forwarded-For`, `X-Forwarded-Proto`.
- Chạy nhiều instance backend: job huỷ đơn quá hạn chạy ở mọi instance — an toàn (khoá đơn + kiểm lại trạng thái) nhưng lặp
  công việc; cân nhắc ShedLock nếu scale lớn.
- Reverse proxy phải hỗ trợ WebSocket (upgrade header) cho `/ws`.
- Quy trình deploy: tự động qua CI khi merge vào `main`, hay deploy thủ công.
- Domain/URL demo dùng khi báo cáo đồ án. Không seed tài khoản mẫu dev ở môi trường demo công khai.
