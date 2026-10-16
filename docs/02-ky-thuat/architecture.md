# Kiến trúc hệ thống

```
┌──────────────┐   REST /api (JSON + JWT)   ┌──────────────┐        ┌──────────────┐
│   Next.js    │ ─────────────────────────► │  Spring Boot │ ─────► │  PostgreSQL  │
│   Frontend   │ ◄───────────────────────── │   Backend    │ ◄───── │              │
│              │ ◄══ WebSocket /ws (STOMP) ═│              │        └──────────────┘
└──────────────┘                            └──────┬───────┘
                                                   │
                          ┌────────────────────────┼────────────────────┐
                          ▼                        ▼                    ▼
                  VNPay (sandbox)           SMTP (email)        Cloudinary (ảnh)
                  redirect + IPN
```

## Backend — phân module theo package (không theo layer)

Mỗi package dưới `vn.campvivo.*` là 1 module nghiệp vụ, tự chứa Controller/Service/Repository/
Entity/DTO của mình. Ánh xạ module ↔ bảng: `database.md` mục 2.

| Module | Trách nhiệm | Bảng sở hữu |
|---|---|---|
| `auth` | Đăng ký, OTP, đăng nhập/đăng xuất, JWT filter, security config | (dùng `users`) |
| `user` | Hồ sơ, đổi mật khẩu, quản lý user (admin) | `users` |
| `catalog` | Danh mục, sản phẩm, ảnh, tìm kiếm, brands | `categories`, `products`, `images` |
| `combo` | Combo + thành phần | `product_combos`, `combo_items` |
| `inventory` | Xem/điều chỉnh tồn kho, nhật ký; API nội bộ reserve/release/commit/restock | `inventory_logs` (cập nhật cột tồn kho của products/product_combos) |
| `cart` | Giỏ hàng | `carts`, `cart_items` |
| `order` | Tạo đơn, máy trạng thái, huỷ, trả hàng, job timeout | `orders`, `order_items`, `order_status_history`, `return_requests` |
| `payment` | `PaymentGateway`, VNPay, IPN/return | `payments` |
| `promotion` | Voucher, chiến dịch, `PricingService` (tính giá BR-4/5/6) | `vouchers`, `discount_campaigns`, `discount_campaign_products` |
| `review` | Đánh giá | `reviews` |
| `favorite` | Yêu thích | `favorites` |
| `article` | Bài viết | `articles` |
| `support` | Chat hỗ trợ | `support_messages` |
| `notification` | Thông báo in-app + WebSocket + gửi email (template) | `notifications` |
| `recommendation` | Gợi ý co-purchase (chỉ đọc) | — |
| `analytics` | Thống kê dashboard (chỉ đọc) | — |
| `common` | config (security, websocket, async, cors), exception, `ApiResponse`, util (slug, money, sanitize) | — |

Không còn module `rental` (D-01). `chatbot` sẽ thêm ở giai đoạn sau (D-05).

Cấu trúc trong một module:
```
order/
  OrderController.java        AdminOrderController.java
  OrderService.java           ReturnService.java      OrderTimeoutJob.java
  Order.java  OrderItem.java  OrderStatus.java  ...   (entity + enum)
  OrderRepository.java ...
  dto/ CreateOrderRequest.java  OrderDetailResponse.java ...
  event/ OrderStatusChangedEvent.java ...
```

Luồng xử lý 1 request: `Controller` (nhận & validate DTO, phân quyền) → `Service` (`@Transactional`,
nghiệp vụ) → `Repository` (Spring Data JPA). Entity không trả thẳng ra API — luôn qua DTO.

## Nguyên tắc module hoá
- Module không gọi Repository của module khác — gọi qua Service public của module đó. Ví dụ
  `OrderService` gọi `InventoryService.reserve(...)`, `PricingService.priceCart(...)`,
  `VoucherService.consume(...)`, `PaymentGatewayResolver.get(method)`.
- Entity được tham chiếu chéo module bằng `@ManyToOne(fetch = LAZY)` khi cần (vd. `OrderItem → Product`),
  nhưng thay đổi dữ liệu module khác luôn qua Service của module đó.
- Module backend ↔ feature frontend tương ứng 1-1 khi có thể.
- Đổi kiến trúc này cần thống nhất lại và ghi vào `docs/99-ghi-chu/decisions.md`.

## Sự kiện & side-effect (D-15)
- Nghiệp vụ phát sự kiện bằng `ApplicationEventPublisher`: `OrderCreatedEvent`,
  `OrderStatusChangedEvent`, `ReturnRequestChangedEvent`, `SupportMessageCreatedEvent`.
- Module `notification` nghe bằng `@TransactionalEventListener(phase = AFTER_COMMIT)`: tạo bản ghi
  `notifications` (transaction mới, `REQUIRES_NEW`), đẩy WebSocket, gửi email `@Async`.
- Module `order` KHÔNG gọi trực tiếp `notification`. Giao dịch rollback → không có thông báo/email.
- Ngoại lệ: email OTP gửi đồng bộ trong `auth` (người dùng cần biết ngay nếu gửi lỗi).

## Thanh toán mở rộng được (D-08)
```java
public interface PaymentGateway {
    PaymentMethod method();                                  // VNPAY, (MOMO sau này)
    String createPaymentUrl(Order order, String clientIp);   // URL redirect
    IpnResult verifyIpn(Map<String, String> params);         // kiểm chữ ký, trích orderCode, amount, success, transactionId
}
```
- Hiện có `VnpayGateway`. `PaymentGatewayResolver` chọn theo `PaymentMethod`. COD không cần gateway.
- Thêm MoMo = thêm `MomoGateway` + giá trị enum + endpoint IPN `/payments/momo/ipn`; không sửa `OrderService`.
- Xử lý kết quả IPN dùng chung `PaymentService.handleIpnResult(...)` (BR-12).

## Realtime (D-15)
- Spring WebSocket + STOMP, endpoint `/ws`, simple broker `/queue`, user destination `/user`.
- Gửi bằng `SimpMessagingTemplate.convertAndSendToUser(userId, "/queue/notifications", dto)`.
- Chat: tin của khách → gửi tới mọi Staff đang kết nối (`/user/{staffId}/queue/support`); tin của
  nhân viên → gửi tới khách.

## Job định kỳ
- `OrderTimeoutJob` (BR-3) — `@Scheduled(fixedDelay = 60000)`.
- Bật `@EnableScheduling`, `@EnableAsync` trong `common/config`.

## Frontend — phân theo feature
```
src/app/              Next.js App Router: (shop)/ (auth)/ account/ admin/
src/components/ui/        component dùng chung (Button, Modal, Toast, Input, Pagination...)
src/components/features/<module>/  component nghiệp vụ (cart, order, product, account...)
src/services/          gọi API theo module (+ apiClient.ts); websocket client (notification, support)
src/types/              kiểu dữ liệu khớp docs/02-ky-thuat/api.md
src/hooks/              hook dùng chung (useAuth, useCart, useNotifications...)
src/mocks/              dữ liệu mock (giai đoạn chưa có API — xoá dần khi nối API)
```
- SSR cho trang public (sản phẩm, combo, bài viết, danh mục) — gọi API server-side.
- CSR cho giỏ hàng, thanh toán, tài khoản, admin.
- Trạng thái đăng nhập/giỏ hàng: React context (`ShopProvider` hiện tại sẽ chuyển sang gọi API).
