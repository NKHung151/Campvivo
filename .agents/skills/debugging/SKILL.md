---
name: debugging
description: Phân tích nguyên nhân gốc và sửa lỗi, đặc biệt lỗi liên quan tồn kho sai lệch,
  đơn hàng kẹt trạng thái, webhook thanh toán.
---

# Debugging

## Khi nào dùng
Khi có bug report hoặc hành vi hệ thống không đúng kỳ vọng.

## Quy trình
1. Tái hiện lỗi trước, xác định bước chính xác gây lỗi — không sửa mò.
2. Với lỗi tồn kho sai: kiểm tra `order_status_history` và log transaction để biết chính
   xác bước nào tăng/giảm `reserved_quantity`/`stock_quantity` sai so với quy tắc BR-2 trong
   `docs/02-ky-thuat/database.md`.
3. Với lỗi đơn hàng kẹt trạng thái: đối chiếu bảng chuyển trạng thái hợp lệ trong
   `database.md`, kiểm tra có bước nào bỏ qua transaction hoặc exception bị nuốt
   (catch rỗng) không.
4. Với lỗi thanh toán: kiểm tra log IPN `/api/payments/vnpay/ipn`, chữ ký, `transaction_id`,
   trạng thái `payments` (BR-12); local không nhận IPN → xem `docs/04-van-hanh/troubleshooting.md`.
   Thanh toán thành công cho đơn đã huỷ là hành vi đã biết (known-issues KI-01).
5. Với voucher lệch `used_count`: đối chiếu số đơn dùng voucher ở trạng thái khác
   CANCELLED/PAYMENT_FAILED; kiểm tra luồng huỷ/IPN thất bại/job timeout có trả lượt không (BR-5).
6. Với thông báo/email không gửi: kiểm tra event có được publish trong transaction đã commit không
   (listener AFTER_COMMIT không chạy khi rollback), WebSocket principal = userId.
7. Sửa tại gốc, không patch triệu chứng. Thêm test tái hiện lỗi để tránh tái phát.
8. Nếu lỗi do thiết kế sai (không phải bug code) → ghi vào
   `docs/99-ghi-chu/known-issues.md` hoặc `decisions.md` tuỳ trường hợp.

## Tiêu chí hoàn thành
- Lỗi không còn tái hiện.
- Có test mới cho đúng kịch bản lỗi.
