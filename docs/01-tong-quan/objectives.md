# Mục tiêu dự án

1. Xây dựng website TMĐT hoàn chỉnh cho luồng mua hàng (Tier 1): đăng ký có xác thực email, tìm
   kiếm, giỏ hàng, đặt hàng, thanh toán trực tuyến (VNPay) và COD, theo dõi/huỷ đơn, quản trị sản
   phẩm/danh mục/tồn kho/đơn hàng/người dùng.
2. Bổ sung các tính năng nâng cao (Tier 2) thể hiện chiều sâu kỹ thuật: combo, voucher và chiến
   dịch giảm giá, đánh giá, trả hàng có duyệt, yêu thích, bài viết, dashboard thống kê, gợi ý sản
   phẩm dựa trên lịch sử mua chung.
3. Tương tác thời gian thực: thông báo in-app và chat hỗ trợ giữa khách hàng và nhân viên qua
   WebSocket; email tự động theo sự kiện.
4. Giải quyết đúng bài toán đồng thời trong TMĐT: chống bán vượt tồn kho (sản phẩm và combo) và
   chống vượt số lượt voucher bằng transaction + khoá bi quan theo thứ tự cố định; IPN thanh toán
   idempotent, xác minh chữ ký.
5. Thiết kế mở rộng được: thêm cổng thanh toán (MoMo) và chatbot AI mà không phải sửa luồng nghiệp
   vụ cốt lõi.
