# Luồng người dùng chính

Ký hiệu: `→` bước tiếp theo, `[API]` endpoint gọi, `⚠` nhánh lỗi. Quy tắc chi tiết: BR-xx trong
`docs/02-ky-thuat/database.md`; route: `pages.md`.

## 1. Đăng ký và xác thực email (FR-01)
```
/register: nhập email, mật khẩu, họ tên  [POST /auth/register]
  ⚠ EMAIL_ALREADY_EXISTS → gợi ý đăng nhập / quên mật khẩu
→ /verify-email?email=…: nhập OTP 6 số   [POST /auth/verify-email]
  ⚠ INVALID_OTP / OTP_EXPIRED / OTP_TOO_MANY_ATTEMPTS → nút "Gửi lại mã" (đếm ngược 60s) [POST /auth/resend-code]
→ thành công: nhận token, đã đăng nhập, quay về trang trước
```

## 2. Đăng nhập / đăng xuất (FR-02)
```
/login (hoặc popup): email + mật khẩu   [POST /auth/login]
  ⚠ INVALID_CREDENTIALS → báo sai thông tin
  ⚠ EMAIL_NOT_VERIFIED → tự gọi resend-code → chuyển /verify-email
  ⚠ ACCOUNT_DISABLED → báo tài khoản bị khoá
→ lưu token → về ?redirect hoặc trang trước; Staff → /admin
Đăng xuất  [POST /auth/logout] → xoá token → về trang chủ (mọi thiết bị khác cũng bị đăng xuất)
```

## 3. Quên mật khẩu (FR-03)
```
/forgot-password: nhập email  [POST /auth/forgot-password]  (luôn báo "nếu email tồn tại, mã đã được gửi")
→ /reset-password?email=…: OTP + mật khẩu mới  [POST /auth/reset-password]
→ thành công → /login
```

## 4. Mua hàng (FR-07 → FR-11) — luồng quan trọng nhất
```
Khám phá: /, /products, /categories/[slug], /combos, /search
→ /products/[slug] hoặc /combos/[slug]: xem giá (gốc + sau chiến dịch), tồn kho khả dụng
→ "Thêm vào giỏ"  [POST /cart/items]   (chưa đăng nhập → /login?redirect=…)
  ⚠ OUT_OF_STOCK → báo số lượng còn lại;  ⚠ PRODUCT_INACTIVE
→ /cart: danh sách dòng với giá HIỆN HÀNH  [GET /cart]
   sửa số lượng [PATCH /cart/items/{id}] / xoá [DELETE]
   dòng hết hàng/ngừng bán được đánh dấu, phải xử lý trước khi đặt
→ phần thanh toán cùng trang:
   form người nhận tự điền từ hồ sơ (tick "lưu làm mặc định")
   nhập voucher [POST /vouchers/validate] → hiện số tiền giảm hoặc lỗi (không chặn đặt hàng, bỏ mã là đặt được)
   chọn VNPay / COD
   tóm tắt tiền [POST /orders/preview] → giữ finalAmount làm expectedTotal
→ "Đặt hàng"  [POST /orders]  → đơn PENDING, tồn kho được giữ chỗ, voucher bị trừ lượt, giỏ được dọn
  ⚠ PRICE_CHANGED → hiện bảng giá mới, khách bấm xác nhận lại (gửi expectedTotal mới)
  ⚠ OUT_OF_STOCK → hiện mặt hàng thiếu, quay lại sửa giỏ
  ⚠ lỗi voucher → bỏ mã hoặc nhập mã khác
  ⚠ PAYMENT_GATEWAY_ERROR → báo thử lại (không có đơn nào được tạo)
→ COD: /checkout/success?orderId=… "Đặt hàng thành công, chờ xác nhận"
→ VNPay: chuyển tới paymentUrl → khách thanh toán trên VNPay
     VNPay gọi IPN (server-to-server) → đơn CONFIRMED hoặc PAYMENT_FAILED
     VNPay chuyển khách về /checkout/success?… [GET /payments/vnpay/return] → hiển thị trạng thái
     (IPN chưa tới → hiển thị "đang xử lý", hỏi lại GET /orders/{id} vài lần)
     khách bỏ dở → sau 15 phút đơn tự huỷ, giải phóng tồn kho và lượt voucher
→ nhân viên xử lý (luồng 7) → SHIPPING → COMPLETED
→ khách nhận thông báo + email ở mỗi bước; sau COMPLETED có thể đánh giá hoặc yêu cầu trả hàng
```

## 5. Huỷ đơn (FR-12)
```
/account/orders/[id] → đơn PENDING → "Huỷ đơn" (xác nhận)  [POST /orders/{id}/cancel]
→ CANCELLED → giải phóng giữ chỗ, trả lượt voucher
  ⚠ ORDER_NOT_CANCELLABLE (đơn vừa được xác nhận/thanh toán) → tải lại trạng thái
```

## 6. Trả hàng (FR-19)
```
Khách: /account/orders/[id] → đơn COMPLETED, còn trong 7 ngày → "Yêu cầu trả hàng", nhập lý do
       [POST /orders/{id}/returns] → yêu cầu PENDING; nhân viên nhận thông báo
  ⚠ RETURN_WINDOW_EXPIRED / RETURN_ALREADY_EXISTS / RETURN_NOT_ALLOWED
Nhân viên: /admin/returns → mở yêu cầu → Duyệt (APPROVED) hoặc Từ chối (REJECTED, bắt buộc ghi chú)
       → khi nhận lại hàng → "Xác nhận đã nhận hàng" (COMPLETED)
       → hệ thống cộng lại tồn kho (ghi nhật ký RETURN), đơn chuyển RETURNED
Khách nhận thông báo + email ở mỗi bước. Hoàn tiền thực hiện ngoài hệ thống.
```

## 7. Nhân viên xử lý đơn (FR-13)
```
/admin/orders → lọc trạng thái → /admin/orders/[id]
→ PENDING + COD: "Xác nhận" (CONFIRMED, trừ tồn kho) hoặc "Huỷ" (bắt buộc ghi chú)
  (PENDING + VNPay: không có nút — chờ IPN hoặc tự huỷ khi quá hạn)
→ CONFIRMED: "Giao hàng" + nhập mã vận đơn vào ghi chú → SHIPPING
→ SHIPPING: "Hoàn tất" → COMPLETED (COD: ghi nhận đã thu tiền)
Mỗi bước ghi lịch sử, gửi thông báo + email cho khách.
  ⚠ INVALID_STATUS_TRANSITION → tải lại đơn (có thể người khác vừa xử lý)
```

## 8. Điều chỉnh tồn kho (FR-08)
```
/admin/inventory → lọc sản phẩm/combo, "sắp hết hàng" → chọn mặt hàng → "Điều chỉnh"
→ nhập số lượng (+ nhập / − xuất), lý do (IMPORT/EXPORT/ADJUST), ghi chú
   [POST /admin/inventory/adjustments] → cập nhật stock, ghi nhật ký
  ⚠ INVALID_STOCK_ADJUSTMENT (xuất nhiều hơn phần không bị giữ chỗ)
```

## 9. Đánh giá (FR-18)
```
/account/orders/[id] (đơn COMPLETED) → "Viết đánh giá" ở dòng hàng → sao 1–5 + nội dung  [POST /reviews]
  ⚠ REVIEW_ALREADY_EXISTS → chuyển sang sửa
/account/reviews → sửa [PUT /reviews/{id}] / xoá [DELETE /reviews/{id}]
```

## 10. Yêu thích (FR-20)
```
Biểu tượng trái tim ở thẻ/chi tiết sản phẩm, combo, bài viết → [POST /favorites] / [DELETE /favorites?…]
(chưa đăng nhập → /login) → /account/favorites xem theo tab
```

## 11. Chat hỗ trợ (FR-26)
```
Khách: /account/support → tải lịch sử [GET /support/messages] → gửi [POST /support/messages]
       → nhận trả lời realtime qua WS /user/queue/support (+ thông báo SUPPORT_MESSAGE)
Nhân viên: /admin/support → danh sách hội thoại (mới nhất lên đầu) → mở hội thoại
       → trả lời [POST /admin/support/conversations/{customerId}/messages]
       → tin mới của khách đến realtime, hội thoại nhảy lên đầu
```

## 12. Thông báo (FR-22)
```
Sự kiện (đơn đổi trạng thái, trả hàng, tin nhắn hỗ trợ, đơn mới/yêu cầu trả hàng mới cho nhân viên)
→ lưu notifications → đẩy WS /user/queue/notifications → chuông tăng số chưa đọc + toast
→ bấm thông báo → đánh dấu đã đọc [PATCH /notifications/{id}/read] → đi tới link
→ /account/notifications: lọc, "Đánh dấu tất cả đã đọc" [PATCH /notifications/read-all]
```

## 13. Quản trị nội dung (FR-05, 06, 14–17, 21) — tóm tắt
- Danh mục: tạo/sửa (chọn cha), xoá khi rỗng.
- Sản phẩm/combo: tạo → upload ảnh → lưu → nhập tồn kho ban đầu ở `/admin/inventory`.
- Voucher/chiến dịch: tạo với thời hạn; chiến dịch chọn sản phẩm áp dụng; giá hiển thị cập nhật ngay.
- Bài viết: soạn → lưu nháp → xuất bản.
- Người dùng: tìm → đổi role (vd. cấp quyền EMPLOYEE) hoặc khoá.
