# Vấn đề cần giải quyết

- Người đi dã ngoại/du lịch trải nghiệm cần mua trang thiết bị (lều, túi ngủ, bếp cắm trại,
  balo...) nhưng các kênh hiện tại rời rạc, thiếu thông tin chi tiết, khó so sánh và không
  có gợi ý phối hợp sản phẩm (combo, mua kèm) theo nhu cầu chuyến đi.
- Người mới thiếu kiến thức chọn đồ và kinh nghiệm outdoor; khi phân vân cần một kênh hỏi trực
  tiếp người bán thay vì gọi điện hay nhắn qua mạng xã hội.
- Người bán nhỏ lẻ thiếu công cụ quản lý tập trung: tồn kho (kể cả hàng đang giữ chỗ), đơn hàng,
  trả hàng, khuyến mãi, nội dung và doanh số.
- Bài toán kỹ thuật cốt lõi: đảm bảo không bán vượt tồn kho và không phát vượt voucher khi nhiều
  khách mua cùng lúc (concurrency); tích hợp thanh toán trực tuyến thật (VNPay sandbox) an toàn,
  idempotent; thông báo thời gian thực.
