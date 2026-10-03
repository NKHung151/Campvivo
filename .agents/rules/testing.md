# Quy tắc Testing

- Luồng bắt buộc phải có integration test: tạo đơn hàng + giữ/trừ tồn kho (chống oversell),
  webhook thanh toán (idempotency), áp mã khuyến mãi, kiểm tra quyền viết review.
- Backend: dùng Testcontainers với PostgreSQL thật cho test liên quan transaction/locking,
  không mock DB cho các test này.
- Test race condition tối thiểu 1 lần cho luồng đặt hàng: gọi song song nhiều request đặt
  cùng 1 sản phẩm còn ít tồn kho, assert không có đơn nào vượt tồn kho.
- Frontend: test các state quan trọng của luồng checkout (giỏ hàng rỗng, hết hàng giữa
  chừng, mã giảm giá hết hạn, thanh toán thất bại).
- Trước khi coi 1 task là xong: `mvn test` (backend) và `npm run lint && npm run build`
  (frontend) phải xanh.
- Không xoá hoặc comment-out test để "cho qua" CI.
