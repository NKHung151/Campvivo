# Mục tiêu dự án

1. Xây dựng website TMĐT hoàn chỉnh cho luồng mua hàng (Tier 1): tìm kiếm, giỏ hàng, đặt
   hàng, thanh toán trực tuyến, theo dõi đơn, quản trị sản phẩm/đơn hàng.
2. Bổ sung các tính năng nâng cao (Tier 2) thể hiện chiều sâu kỹ thuật: combo sản phẩm,
   khuyến mãi, đánh giá, thông báo tự động, dashboard thống kê, gợi ý sản phẩm dựa trên
   lịch sử mua hàng.
3. Chứng minh khả năng mở rộng kiến trúc sang mô hình cho thuê (Tier 3) ở quy mô nhỏ, có
   kiểm soát rủi ro tiến độ.
4. Giải quyết đúng đắn bài toán đồng thời (concurrency) trong thương mại điện tử: chống bán
   vượt tồn kho bằng transaction/locking, đảm bảo idempotency cho webhook thanh toán.
