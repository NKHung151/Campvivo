# Thiết kế cơ sở dữ liệu

18 bảng: Tier 1 (12 bảng) + Tier 2 (6 bảng). Tier 3 (rental) thêm bảng sau khi mở, không
tạo trước.

## Danh sách bảng
**Tier 1:** `roles`, `users`, `user_addresses`, `categories`, `products`, `product_images`,
`carts`, `cart_items`, `orders`, `order_items`, `order_status_history`, `payments`

**Tier 2:** `promotions`, `promotion_usages`, `reviews`, `product_combos`, `combo_items`,
`email_notifications`

**Tier 3 (thêm sau, chưa tạo):** cột `is_rentable`, `rental_price_per_day` trên `products`;
bảng `rental_availability`, `deposits`.

ERD đầy đủ (Mermaid) và DDL SQL chi tiết: xem file ERD/DDL riêng của nhóm (tài liệu Tuần 1-2).
Khi schema thay đổi, cập nhật file đó và tóm tắt lại ở đây.

## Quy tắc nghiệp vụ bắt buộc tuân thủ khi code

**BR-1 — Tồn kho khả dụng:**
```
available_quantity = stock_quantity - reserved_quantity
```
Không cho thêm giỏ hàng hoặc tạo đơn vượt quá `available_quantity`.

**BR-2 — Vòng đời giữ/trừ tồn kho:**
- Tạo đơn (`PENDING`) → tăng `reserved_quantity`.
- Đơn chuyển `CONFIRMED` (thanh toán thành công / admin duyệt COD) → trừ cả
  `stock_quantity` và `reserved_quantity` tương ứng.
- Đơn `CANCELLED` hoặc `PAYMENT_FAILED` → chỉ giảm `reserved_quantity`, không đụng
  `stock_quantity`.

**BR-3 — Timeout đơn hàng:** đơn `PENDING` quá 15 phút chưa thanh toán online → job
`@Scheduled` tự chuyển `CANCELLED`.

**BR-4 — Chốt giá:** giá lấy theo `cart_items.price_at_add` khi tạo đơn; `order_items.
unit_price` không đổi sau khi đơn đã tạo, kể cả khi giá sản phẩm thay đổi sau đó.

**BR-5 — Khuyến mãi:** mã phải `is_active = true`, chưa hết hạn (`expiry_date`), đơn hàng
đạt `min_order_value`; số tiền giảm không vượt tổng tiền đơn.

**BR-6 — Đánh giá:** chỉ cho phép khi đơn hàng của chính user chứa sản phẩm đó và đơn ở
trạng thái `COMPLETED`; mỗi (product, user, order) chỉ 1 đánh giá — constraint
`unique_user_product_order_review`.

**BR-7 — Lịch sử trạng thái:** mọi lần đổi `orders.status` phải ghi 1 dòng vào
`order_status_history` (old_status, new_status, changed_by, note).

## Máy trạng thái đơn hàng
```
PENDING ──► CONFIRMED ──► SHIPPING ──► COMPLETED
   │
   ├──► CANCELLED
   └──► PAYMENT_FAILED
```
| Từ | Đến | Ai thực hiện | Điều kiện |
|---|---|---|---|
| (mới) | PENDING | Customer | Checkout thành công, giữ chỗ tồn kho |
| PENDING | CONFIRMED | System/Admin | Webhook thanh toán SUCCESS, hoặc Admin duyệt COD |
| PENDING | CANCELLED | Customer/System | Khách hủy hoặc hết hạn 15 phút |
| PENDING | PAYMENT_FAILED | System | Webhook báo thất bại |
| CONFIRMED | SHIPPING | Employee/Admin | Đã bàn giao vận chuyển |
| SHIPPING | COMPLETED | Employee/Admin | Khách đã nhận hàng |

## Logic lock chống bán vượt tồn kho (mẫu)
```java
@Transactional
public Order createOrder(Long cartId, Long addressId, String paymentMethod) {
    Cart cart = cartRepo.findByIdForUpdate(cartId);
    List<CartItem> sorted = cart.getItems().stream()
        .sorted(Comparator.comparing(CartItem::getProductId)) // tránh deadlock
        .toList();
    for (CartItem item : sorted) {
        Product p = productRepo.findByIdForUpdate(item.getProductId()); // SELECT ... FOR UPDATE
        if ((p.getStockQuantity() - p.getReservedQuantity()) < item.getQuantity())
            throw new OutOfStockException(p.getName());
        p.setReservedQuantity(p.getReservedQuantity() + item.getQuantity());
    }
    // tạo Order, OrderItem, OrderStatusHistory...
}
```

## Index bắt buộc
Mọi FK dùng để JOIN/filter thường xuyên phải có index, đặt tên `idx_<bang>_<cot>` (vd.
`idx_orders_user_id`, `idx_products_category_id`). Danh mục đa cấp dùng cột `path` dạng
`/1/5/12/` với index `varchar_pattern_ops` để tránh recursive CTE.
