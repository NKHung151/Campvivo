# Troubleshooting

Ghi lại các lỗi hay gặp trong quá trình phát triển và cách xử lý, để không phải debug lại
từ đầu mỗi lần gặp.

## Mẫu
### <Tên lỗi ngắn gọn>
- **Triệu chứng:**
- **Nguyên nhân:**
- **Cách xử lý:**

---

### IPN VNPay không về khi chạy local
- **Triệu chứng:** Thanh toán xong trên sandbox nhưng đơn vẫn PENDING, rồi bị huỷ sau 15 phút.
- **Nguyên nhân:** VNPay không gọi được `localhost`; IPN URL chưa khai báo hoặc ngrok đổi URL.
- **Cách xử lý:** Chạy ngrok, cập nhật `app.vnpay.ipn-url` và IPN URL trên trang sandbox
  (`local-development.md`). Kiểm tra log endpoint `/api/payments/vnpay/ipn`.

### IPN báo sai chữ ký
- **Triệu chứng:** IPN trả `RspCode=97`.
- **Nguyên nhân:** Sai `VNPAY_HASH_SECRET`; không sắp xếp tham số theo alphabet hoặc encode khác
  cách VNPay; đưa cả `vnp_SecureHash`/`vnp_SecureHashType` vào chuỗi ký.
- **Cách xử lý:** Đối chiếu thuật toán với code mẫu Java của VNPay; viết unit test với bộ tham số mẫu.

### Deadlock khi test đặt hàng song song
- **Triệu chứng:** `PSQLException: deadlock detected`.
- **Nguyên nhân:** Khoá products/combos/voucher không theo thứ tự cố định.
- **Cách xử lý:** Theo thứ tự `orders → products (id tăng) → product_combos (id tăng) → vouchers`
  (`database.md` mục 6); truy vấn khoá nhiều dòng phải có `ORDER BY id`.

### WebSocket kết nối được nhưng không nhận thông báo
- **Triệu chứng:** Client subscribe `/user/queue/notifications` nhưng không có tin.
- **Nguyên nhân:** Chưa gắn `Principal` khi CONNECT, hoặc tên principal khác giá trị dùng trong
  `convertAndSendToUser` (phải là `userId` dạng chuỗi).
- **Cách xử lý:** Kiểm tra `ChannelInterceptor` xác thực JWT và `setUser(...)`.

### Không nhận được email OTP
- **Triệu chứng:** Đăng ký trả lỗi `EMAIL_SEND_FAILED` hoặc không thấy thư.
- **Nguyên nhân:** Sai cấu hình SMTP, Gmail chặn mật khẩu thường.
- **Cách xử lý:** Dùng App Password hoặc Mailpit khi dev.
