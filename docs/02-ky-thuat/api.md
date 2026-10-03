# API Contract v1

Chuẩn OpenAPI/Swagger. File này là bản tóm tắt — khi thêm/sửa endpoint, cập nhật ở đây
TRƯỚC khi code (xem skill `api-contract`).

## Quy ước chung
- Base path: `/api`
- Auth: header `Authorization: Bearer <JWT>` cho mọi endpoint cần đăng nhập.
- Response lỗi thống nhất:
```json
{ "success": false, "errorCode": "OUT_OF_STOCK", "message": "Chỉ còn 15 sản phẩm khả dụng." }
```
- Response thành công có danh sách: bọc trong `data` + `pagination` nếu có phân trang.

## Endpoints Tier 1

| Method | Path | Actor | Mô tả | Lỗi chính |
|---|---|---|---|---|
| POST | `/auth/register` | Guest | Đăng ký | 400 EMAIL_ALREADY_EXISTS |
| POST | `/auth/login` | Guest | Đăng nhập, trả JWT | 401 INVALID_CREDENTIALS |
| GET/POST/PUT/DELETE | `/users/addresses` | User | Sổ địa chỉ | 400, 403 ADDRESS_ACCESS_DENIED |
| GET | `/products` | Guest | Danh sách, filter, phân trang | 400 |
| GET | `/products/{id}/recommendations` | Guest | 4 sản phẩm gợi ý mua kèm | 404 |
| POST | `/cart/items` | Customer | Thêm giỏ hàng | 409 OUT_OF_STOCK, 400 PRODUCT_INACTIVE |
| POST | `/orders` | Customer | Tạo đơn từ giỏ hàng | 400 INVALID_PROMOTION_CODE, 409 INSUFFICIENT_STOCK_CONCURRENCY, 502 PAYMENT_GATEWAY_ERROR |
| POST | `/payments/webhook` | System (MoMo/VNPay) | Callback thanh toán, idempotent theo `transactionId` | — |
| GET | `/orders/{id}` | Customer | Chi tiết đơn | 403, 404 |
| POST | `/admin/products` | Admin | Thêm sản phẩm + ảnh | 401, 403, 400 |
| POST | `/admin/categories` | Admin | Thêm danh mục | 404 PARENT_CATEGORY_NOT_FOUND, 400 CIRCULAR_CATEGORY_DEPENDENCY |
| PATCH | `/admin/products/{id}/inventory` | Admin/Employee | Điều chỉnh tồn kho | 400 INVALID_STOCK_ADJUSTMENT |
| PATCH | `/admin/orders/{id}/status` | Employee/Admin | Đổi trạng thái đơn | 400, 401, 403, 404 |

## Endpoints Tier 2

| Method | Path | Actor | Mô tả |
|---|---|---|---|
| POST | `/promotions/validate` | Customer | Kiểm tra mã giảm giá hợp lệ, tính số tiền giảm |
| GET | `/combos` | Guest | Danh sách combo |
| POST | `/reviews` | Customer | Viết đánh giá (chỉ khi đơn COMPLETED) — lỗi 400 ORDER_NOT_COMPLETED, 409 REVIEW_ALREADY_EXISTS |
| GET | `/admin/analytics/revenue` | Admin | Doanh thu theo ngày/tháng |
| GET | `/admin/analytics/top-products` | Admin | Top sản phẩm bán chạy |

## Payload mẫu

### `GET /products`
Query: `category=leu&minPrice=100000&maxPrice=500000&sortBy=price_asc&page=1&limit=10`
```json
{
  "success": true,
  "data": [
    { "id": 12, "name": "Lều cắm trại 2 người chống nước", "slug": "leu-cam-trai-2-nguoi",
      "price": 450000.00, "stockQuantity": 15,
      "primaryImageUrl": "https://cdn.campvivo.vn/images/leu-2-nguoi.jpg" }
  ],
  "pagination": { "currentPage": 1, "totalPages": 3, "totalItems": 25 }
}
```

### `POST /cart/items`
```json
{ "productId": 12, "quantity": 2 }
```
Lỗi 409:
```json
{ "success": false, "errorCode": "OUT_OF_STOCK", "message": "Chỉ còn 15 sản phẩm khả dụng trong kho." }
```

### `POST /orders`
```json
{ "cartId": 5, "shippingAddressId": 2, "paymentMethod": "MOMO", "promotionCode": "CAMPVIVO2025" }
```
Response 201:
```json
{
  "success": true,
  "data": {
    "orderId": 108, "orderCode": "ORD-20250315-99",
    "totalAmount": 900000.00, "discountAmount": 90000.00, "finalAmount": 810000.00,
    "status": "PENDING", "paymentUrl": "https://momo.vn/pay/sandbox/ORD-20250315-99"
  }
}
```

### `POST /promotions/validate`
```json
{ "code": "CAMPVIVO2025", "orderValue": 900000.00 }
```
```json
{
  "success": true,
  "data": { "code": "CAMPVIVO2025", "discountType": "PERCENTAGE", "discountValue": 10.00,
    "calculatedDiscount": 90000.00, "isValid": true }
}
```

### `PATCH /admin/orders/{id}/status`
```json
{ "status": "SHIPPING", "note": "Đã bàn giao cho đơn vị vận chuyển." }
```
```json
{ "success": true, "data": { "orderId": 108, "oldStatus": "CONFIRMED", "newStatus": "SHIPPING",
  "updatedAt": "2025-03-15T09:15:00Z" } }
```
