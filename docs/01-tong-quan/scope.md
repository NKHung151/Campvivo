# Phạm vi dự án

## Trong phạm vi
- Toàn bộ Tier 1 (xem `project-overview.md`).
- Tier 2: combo, khuyến mãi, đánh giá, thông báo email tự động, dashboard doanh thu cơ bản,
  gợi ý sản phẩm theo lịch sử mua chung (co-purchase, không phải ML phức tạp).
- Tier 3 (có điều kiện): cho thuê giới hạn ở nhóm sản phẩm nhỏ, tái sử dụng luồng `orders`
  hiện có, không xây dựng vòng đời đơn hàng riêng cho thuê.

## Ngoài phạm vi (không làm trong đồ án này)
- Tích hợp API vận chuyển thật với đơn vị giao nhận (GHN/GHTK...) — mô phỏng trạng thái.
- Biến thể sản phẩm phức tạp (màu/size) — không cần cho đồ dã ngoại.
- SMS tự động (chỉ làm email).
- Mô hình gợi ý bằng Machine Learning thật sự — chỉ dùng truy vấn SQL co-purchase đơn giản.
- Thuê áp dụng cho toàn bộ catalog — chỉ nhóm sản phẩm nhỏ được đánh dấu riêng.
