# Luồng người dùng chính

## Luồng mua hàng (Tier 1 — quan trọng nhất)
```
Guest xem /products → chọn sản phẩm /products/[slug]
   → đăng nhập/đăng ký nếu chưa có tài khoản
   → thêm vào giỏ (/cart)
   → /checkout: chọn địa chỉ, áp mã giảm giá, chọn phương thức thanh toán
   → đặt hàng (status: PENDING, giữ chỗ tồn kho)
   → thanh toán MoMo/VNPay hoặc chọn COD
   → thanh toán OK → CONFIRMED → Employee xử lý → SHIPPING → COMPLETED
   → khách có thể đánh giá sản phẩm đã mua
```

## Luồng hủy đơn
```
/account/orders/[id] → đơn đang PENDING → bấm Hủy đơn
   → CANCELLED → tồn kho giữ chỗ được giải phóng
```

## Luồng admin xử lý đơn
```
/admin/orders → lọc theo trạng thái → mở chi tiết đơn
   → CONFIRMED: xác nhận đã nhận thanh toán (hoặc duyệt COD)
   → SHIPPING: nhập mã vận đơn, bàn giao
   → COMPLETED: xác nhận khách đã nhận hàng
```

## Luồng khuyến mãi (Tier 2)
```
Customer nhập mã ở /checkout → gọi /promotions/validate
   → hợp lệ: hiển thị số tiền giảm → áp vào tổng khi tạo đơn
   → không hợp lệ: hiển thị lỗi, không chặn tiếp tục đặt hàng (bỏ mã hoặc nhập lại)
```
