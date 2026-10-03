# Xác thực & Phân quyền

## Cơ chế
JWT stateless (header `Authorization: Bearer <token>`) + Spring Security Filter Chain.
Mật khẩu băm bằng BCrypt. Token chứa `userId` và `roles`.

## Mô hình kế thừa actor
```
Guest → User → Customer | Employee | Admin
```

| Actor | Kế thừa | Quyền chính |
|---|---|---|
| Guest | — | Xem/tìm/lọc sản phẩm, đăng ký, đăng nhập |
| User | Guest | Hồ sơ cá nhân, đổi mật khẩu, sổ địa chỉ |
| Customer | User | Giỏ hàng, mã giảm giá, đặt hàng, thanh toán, xem/hủy đơn (khi PENDING), đánh giá (khi đơn COMPLETED) |
| Employee | User | Xem toàn bộ đơn hàng, cập nhật CONFIRMED→SHIPPING→COMPLETED, xem cảnh báo tồn kho |
| Admin | User | Toàn quyền: danh mục, sản phẩm, khuyến mãi, combo, dashboard, quản lý user/role |

## Quy tắc code
- Kiểm tra quyền ở tầng Controller bằng annotation (`@PreAuthorize("hasRole('ADMIN')")`
  hoặc tương đương), không chỉ dựa vào việc ẩn UI ở frontend.
- Mọi endpoint `/admin/**` bắt buộc chặn nếu role không phải `EMPLOYEE`/`ADMIN` tuỳ endpoint.
- Không log token hoặc mật khẩu.
