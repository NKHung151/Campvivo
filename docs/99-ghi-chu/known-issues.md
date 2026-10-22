# Known Issues

Ghi lại các vấn đề đã biết nhưng chưa sửa (hoặc cố tình chưa sửa vì ngoài phạm vi), để
tránh agent khác tưởng là bug mới và sửa lặp hoặc sửa sai hướng.

## Mẫu
### <Tên vấn đề>
- **Mô tả:**
- **Ảnh hưởng:**
- **Vì sao chưa sửa / có sửa không:**

---

### KI-01 — Thanh toán VNPay thành công sau khi đơn đã bị huỷ do quá hạn
- **Mô tả:** Khách thanh toán chậm, IPN đến sau khi job BR-3 đã huỷ đơn.
- **Ảnh hưởng:** Khách đã trả tiền nhưng đơn ở CANCELLED. Hệ thống ghi payment `SUCCESS`, đơn giữ
  nguyên, log WARN (BR-12).
- **Vì sao chưa sửa:** Hoàn tiền thủ công ngoài hệ thống. Giảm khả năng xảy ra bằng `vnp_ExpireDate`
  trùng thời gian timeout.

### KI-02 — Chat hỗ trợ không lưu nhân viên nào trả lời, không có trạng thái đã đọc
- **Mô tả:** `support_messages` chỉ có `is_from_staff` (D-05).
- **Ảnh hưởng:** Không thống kê được theo nhân viên; không có badge "tin chưa đọc" trong chat (dùng
  thông báo SUPPORT_MESSAGE thay thế).
- **Vì sao chưa sửa:** Bám sát bảng của báo cáo. Chatbot AI sau này sẽ cần migration bổ sung.

### KI-03 — Đăng xuất là đăng xuất mọi thiết bị
- **Mô tả:** Cơ chế `token_version` (D-11).
- **Vì sao chưa sửa:** Không có bảng refresh token theo thiết bị — chấp nhận cho đồ án.

### KI-04 — Code backend hiện có lệch với docs mới (2026-10-17)
- **Mô tả:** Code khung viết theo docs cũ: `CartItem.priceAtAdd` (D-04 đã bỏ), `OrderStatus` thiếu
  `RETURNED`, entity/DTO chưa có combo, `BusinessException` chưa dùng enum `ErrorCode`; migration
  V1/V2 chỉ là placeholder. Package `rental/` vẫn còn (xoá), chưa có package `favorite/`, `article/`,
  `support/`. `.env.example` còn biến `MOMO_*` (để trống đến FR-27).
- **Xử lý:** Viết lại theo `database.md`/`api.md` khi bắt đầu từng module (plan tạo migration đầy đủ
  trước). Docs là nguồn đúng.

### KI-05 — Frontend storefront dùng dữ liệu mock lệch schema
- **Mô tả:** Đăng nhập OTP giả, đơn hàng/giỏ hàng lưu localStorage, tab "địa chỉ"/"điểm" trong
  `/account`, danh mục con bài viết (`/blog/category/*`, `/journal/category/*`), nội dung/ảnh của
  cửa hàng tham khảo.
- **Xử lý:** Khi nối API theo `pages.md` (cột "API"); xem `.agents/plans/active/frontend-shop-ui.md`.

### KI-06 — Phiên đăng nhập admin đang là bản giả lập
- **Mô tả:** `frontend/src/hooks/useAdminSession.ts` trả user giả (role từ `NEXT_PUBLIC_ADMIN_DEV_ROLE`,
  đổi được bằng ô "Dev" trên header admin, lưu localStorage). Chưa gọi API, chưa có token.
- **Ảnh hưởng:** ai cũng vào được `/admin` ở môi trường dev; nút Đăng xuất bị tắt.
- **Xử lý:** khi có auth thật (N3-03 + F-14) thay ruột `useAdminSession` bằng AuthProvider (`GET /users/me`),
  bỏ ô "Dev". Các trang admin chỉ dùng `useAdmin()` nên không phải sửa.
