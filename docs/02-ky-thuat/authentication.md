# Xác thực & Phân quyền

Nguồn: báo cáo Phase1_v4 (bảng 2.2 — 6 tác nhân, RBAC + thừa kế tác nhân). Quyết định liên quan:
D-10 (OTP), D-11 (token_version), D-15 (WebSocket).

## 1. Tác nhân và role

```
Guest ──► User ──► Customer          Employee, Admin: tác nhân vận hành (đều là User)
System: tác nhân ảo (job @Scheduled, IPN VNPay, event listener) — không có tài khoản
```
- "User" là tác nhân **logic** = bất kỳ ai đã đăng nhập. Trong DB chỉ có cột `users.role`:
  `CUSTOMER` (mặc định khi đăng ký) | `EMPLOYEE` | `ADMIN`. Không có bảng roles.
- Employee/Admin chỉ được tạo bằng cách Admin đổi role của tài khoản đã đăng ký (FR-14), hoặc seed.
- Employee/Admin không mua hàng: endpoint `Customer` chặn role khác CUSTOMER (403).

## 2. Ma trận quyền

| Chức năng | Guest | Customer | Employee | Admin |
|---|:-:|:-:|:-:|:-:|
| Xem trang chủ, sản phẩm, combo, bài viết; tìm kiếm | ✔ | ✔ | ✔ | ✔ |
| Đăng ký, đăng nhập, quên mật khẩu | ✔ | | | |
| Đăng xuất, xem/sửa hồ sơ, đổi mật khẩu | | ✔ | ✔ | ✔ |
| Giỏ hàng, áp voucher, đặt hàng, thanh toán | | ✔ | | |
| Đơn cá nhân: xem, huỷ (PENDING), yêu cầu trả hàng | | ✔ | | |
| Đánh giá (viết/sửa/xoá của mình), yêu thích | | ✔ | | |
| Nhắn tin hỗ trợ (phía khách) | | ✔ | | |
| Xem thông báo của mình | | ✔ | ✔ | ✔ |
| Xem/lọc đơn toàn hệ thống, cập nhật trạng thái đơn | | | ✔ | ✔ |
| Xử lý yêu cầu trả hàng | | | ✔ | ✔ |
| Xem/điều chỉnh tồn kho, xem nhật ký kho | | | ✔ | ✔ |
| Trả lời chat hỗ trợ | | | ✔ | ✔ |
| Quản lý user, danh mục, sản phẩm, combo, voucher, chiến dịch, bài viết | | | | ✔ |
| Dashboard / doanh số | | | | ✔ |

Frontend: Employee vào `/admin` chỉ thấy menu Đơn hàng, Trả hàng, Tồn kho, Hỗ trợ.

## 3. Cơ chế JWT

- Stateless JWT (HS256, secret `JWT_SECRET` ≥ 32 byte), header `Authorization: Bearer <token>`.
- Claims: `sub` = userId, `role`, `tv` = token_version, `iat`, `exp`. Hạn: `app.jwt.ttl` (mặc định 24 giờ).
  Không có refresh token — hết hạn thì đăng nhập lại.
- Filter `JwtAuthenticationFilter` (trước `UsernamePasswordAuthenticationFilter`):
  1. Không có header → tiếp tục như anonymous.
  2. Token sai chữ ký/hết hạn → 401 `UNAUTHORIZED`.
  3. Tải user theo `sub` (có thể cache ngắn); user không tồn tại, `is_active = false`, hoặc
     `tv ≠ users.token_version` → 401.
  4. Đặt `Authentication` với authority `ROLE_<role>` lấy **từ DB** (không tin role trong token).
- `token_version` tăng khi: đăng xuất, đổi mật khẩu, đặt lại mật khẩu, Admin khoá tài khoản hoặc đổi role.
- Frontend lưu token trong `localStorage` (đơn giản cho đồ án); gắn qua `apiClient`; nhận 401 → xoá
  token, chuyển về đăng nhập.
- Mật khẩu băm BCrypt (strength 10). Email chuẩn hoá chữ thường trước khi lưu/tra cứu.

## 4. Luồng OTP (BR-11)

**Đăng ký → xác thực email**
1. `POST /auth/register` → tạo user (`email_verified=false`) + giỏ hàng, sinh OTP `VERIFY_EMAIL`,
   gửi email (đồng bộ — lỗi gửi → `EMAIL_SEND_FAILED`, rollback).
2. Frontend chuyển sang màn nhập mã (`/verify-email?email=...`, dùng lại popup OTP hiện có).
3. `POST /auth/verify-email` đúng mã → `email_verified=true`, xoá OTP, trả token (đăng nhập luôn).
4. Đăng nhập khi chưa xác thực → `EMAIL_NOT_VERIFIED` → frontend gọi `resend-code` và mở màn nhập mã.

**Quên mật khẩu**
1. `POST /auth/forgot-password` → nếu email tồn tại: OTP `RESET_PASSWORD`, gửi email. Luôn trả 200.
2. `POST /auth/reset-password {email, code, newPassword}` → đổi hash, tăng `token_version`, xoá OTP.
   Nếu tài khoản chưa xác thực email thì đặt luôn `email_verified=true` (đã chứng minh sở hữu email).

## 5. Quy tắc code

- Phân quyền bằng `@PreAuthorize` ở Controller (bật `@EnableMethodSecurity`), hoặc theo path trong
  `SecurityFilterChain`:
  - `/api/auth/**`, `GET` catalog/combo/article/search/campaign/brands, `/api/payments/vnpay/**`,
    `/ws/**` → permitAll (IPN vẫn bắt buộc kiểm chữ ký).
  - `/api/admin/**` → `hasAnyRole('EMPLOYEE','ADMIN')`; từng endpoint chỉ dành cho Admin đánh thêm
    `@PreAuthorize("hasRole('ADMIN')")`.
  - `/api/cart/**`, `/api/orders/**`, `/api/reviews/**`, `/api/favorites/**`, `/api/support/**`,
    `/api/vouchers/**` → `hasRole('CUSTOMER')`.
- Quyền sở hữu (đơn, review, favorite, notification của chính mình) kiểm tra ở Service bằng
  `userId` lấy từ `Authentication`, KHÔNG nhận `userId` từ request body/query. Không thuộc về user →
  404 `NOT_FOUND`.
- Ẩn UI ở frontend chỉ là tiện dụng, backend luôn chặn.
- CORS: chỉ cho phép origin frontend (`app.cors.allowed-origins`).
- WebSocket: xác thực JWT trong `ChannelInterceptor` khi CONNECT (cùng logic filter), gắn `Principal`
  = userId để dùng `convertAndSendToUser`.
- Không log mật khẩu, OTP, token, `vnp_SecureHash`, payload thanh toán đầy đủ.
