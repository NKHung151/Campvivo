# Phạm vi dự án

## Trong phạm vi
Toàn bộ 5 phân hệ của báo cáo Phase1_v4 (xem `project-overview.md`, `features.md` FR-01 → FR-26):
- Xác thực email + đặt lại mật khẩu bằng OTP qua email; JWT.
- Danh mục đa cấp, sản phẩm, combo (tồn kho riêng), tồn kho có nhật ký.
- Giỏ hàng (sản phẩm + combo), đặt hàng có giữ chỗ tồn kho, thanh toán **VNPay sandbox + COD**.
- Voucher (giới hạn lượt) và chiến dịch giảm giá theo sản phẩm.
- Đơn cá nhân, huỷ đơn, **trả hàng nguyên đơn** có duyệt.
- Đánh giá (rating + nội dung), yêu thích (sản phẩm, combo, bài viết), bài viết.
- Thông báo in-app realtime, email tự động, **chat hỗ trợ khách ↔ nhân viên** realtime.
- Dashboard doanh số theo ngày/tháng/năm, top mặt hàng.
- Gợi ý mua kèm bằng truy vấn SQL co-purchase.

## Ngoài phạm vi (không làm trong đồ án này)
- Cho thuê sản phẩm (đã bỏ — decisions D-01).
- Sổ nhiều địa chỉ — mỗi user một địa chỉ mặc định (D-02).
- Biến thể sản phẩm (màu/size).
- Tích hợp API vận chuyển thật (GHN/GHTK…) — mô phỏng trạng thái; phí vận chuyển cố định (D-17).
- Hoàn tiền tự động qua cổng thanh toán — trả hàng chỉ ghi nhận, hoàn tiền thủ công (D-07).
- Thanh toán lại cho đơn thất bại; trả hàng từng phần.
- SMS; đăng nhập mạng xã hội; refresh token / đăng xuất từng thiết bị (D-11).
- Gợi ý bằng Machine Learning.
- Bảng thương hiệu riêng, danh mục con cho bài viết, tích điểm thành viên.

## Giai đoạn sau (Tier 3 — chỉ làm khi được yêu cầu rõ)
- Thanh toán MoMo (FR-27).
- Chatbot AI trong kênh chat (FR-28).
