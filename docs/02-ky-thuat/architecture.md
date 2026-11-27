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
                  redirect + IPN            GHN (vận chuyển: địa chỉ, phí ship, vận đơn)
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
| `payment` | `PaymentGateway`, `VnpayService`, `PaymentResultService.handlePaymentResult` (Return URL + IPN) | `payments` |
| `shipping` | `GhnService` (client GHN), `ShippingService` (quy tắc phí ship) — D-27 | — |
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
- Nghiệp vụ phát sự kiện bằng `ApplicationEventPublisher` (danh sách đầy đủ ở bảng bên dưới; tạo đơn dùng chung
  `OrderStatusChangedEvent` với `oldStatus = null`).
- Module `notification` nghe bằng `@TransactionalEventListener(phase = AFTER_COMMIT)`: tạo bản ghi
  `notifications` (transaction mới, `REQUIRES_NEW`), đẩy WebSocket, gửi email `@Async`.
- Module `order` KHÔNG gọi trực tiếp `notification`. Giao dịch rollback → không có thông báo/email.
- Sự kiện module `notification` nghe (D-42 – D-45; record do module phát sở hữu, không import gì của `notification`):

  | Sự kiện | Module phát, chỗ phát | Thông báo sinh ra |
  |---|---|---|
  | `OrderStatusChangedEvent` (đã có) | `order` — `OrderStateService.apply`, `OrderService.create` | `ORDER_STATUS`, `ORDER_NEW`, `ORDER_PAID`; đánh dấu đã xử lý |
  | `ReturnRequestCreatedEvent`, `ReturnRequestProcessedEvent` | `order.returns` — `ReturnService.create/process` | `RETURN_NEW`, `RETURN_STATUS` |
  | `SupportMessageSentEvent` (đã có) | `support` — `SupportService` | `SUPPORT_NEW`, `SUPPORT_MESSAGE` |
  | `StockLevelsChangedEvent` (khả dụng trước/sau, nhân viên gây ra) | `inventory` — `InventoryService.reserve/commit/release/restockReturn`, `AdminInventoryService.adjust`; `combo` — `AdminComboService.pack/unpack` (dùng `inventory.StockLevelSnapshot` chụp trên entity đã khoá) | `LOW_STOCK`, `FAVORITE_BACK_IN_STOCK` |
  | `ReviewFlaggedEvent`, `ReviewModeratedEvent` (`addedToQueue`) | `review` — `ReviewService.create/update/delete`, `AdminReviewService.hide/unhide/clearSensitive/delete` (chỉ khi hàng chờ `status=SENSITIVE` đổi) | `REVIEW_FLAGGED` |
  | `VoucherAnnouncedEvent`, `CampaignStartedEvent` | `promotion` — `PromotionAnnouncementJob` | `VOUCHER_NEW`, `FAVORITE_ON_SALE` |
- Thông báo cho nhiều người (mọi nhân viên, mọi khách, người yêu thích) lưu một dòng cho mỗi người (fan-out — D-42), ghi theo lô
  `app.notification.fan-out-batch-size`. Người yêu thích lấy qua `FavoriteService.customersFavoriting` (khách đang hoạt động —
  D-47); `FavoriteNotificationListener` nghe `CampaignStartedEvent` và `StockLevelsChangedEvent` (riêng với
  `StockNotificationListener` của `LOW_STOCK`).
- **Hàng đợi một luồng (`NotificationDispatcher`, D-46):** listener `AFTER_COMMIT` của `notification` KHÔNG ghi DB tại chỗ mà gửi
  việc vào một hàng đợi một luồng rồi trả về ngay. Lý do: trong `AFTER_COMMIT` luồng nghiệp vụ vẫn giữ kết nối của transaction gốc,
  ghi thêm (`REQUIRES_NEW`) cần kết nối thứ hai → nhiều yêu cầu đồng thời làm cạn pool và treo (đã tái hiện bằng test). Một luồng
  nên sự kiện được xử lý đúng thứ tự commit (vd. "đã xử lý" không chạy trước "tạo"). Lỗi của từng việc chỉ ghi log; việc còn trong
  hàng đợi khi tắt máy được chờ tối đa 10 giây. Không dùng `@Async` mặc định (chạy song song, mất thứ tự) và hàng đợi không phải
  bean `Executor` (không thay executor mặc định mà `OrderShipmentListener` dùng).
- Ngoại lệ: email OTP gửi đồng bộ trong `auth` (người dùng cần biết ngay nếu gửi lỗi).

## Thanh toán mở rộng được (D-08)
```java
public interface PaymentGateway {
    PaymentMethod method();                                  // VNPAY, (MOMO sau này)
    String createPaymentUrl(Order order, String clientIp);   // URL redirect
    IpnResult verifyIpn(Map<String, String> params);         // kiểm chữ ký, trích orderCode, amount, success, transactionId
}
```
- Hiện có `VnpayService implements PaymentGateway`; `PaymentService` chọn cổng theo `PaymentMethod`. COD không cần gateway.
- Thêm MoMo = thêm bean `PaymentGateway` mới + giá trị enum + endpoint callback (vd. `/api/momo/ipn`) gọi
  `PaymentResultService.handlePaymentResult(MOMO, params)`; không sửa `OrderService`.
- Kết quả thanh toán (Return URL và IPN) dùng chung `PaymentResultService.handlePaymentResult` (BR-12, D-28).
- Trạng thái đơn chỉ đổi qua `OrderStateService` (khoá đơn, máy trạng thái, tồn kho qua `InventoryService`, lịch sử, sự kiện).
- Vận đơn GHN: `OrderShipmentListener` nghe `OrderStatusChangedEvent` (→ CONFIRMED, `@Async` + AFTER_COMMIT) gọi
  `OrderShipmentService.createShipment`.

## Realtime (D-15)
- Spring WebSocket + STOMP, endpoint `/ws`, simple broker `/queue`, user destination `/user`.
- Các module KHÔNG gọi `SimpMessagingTemplate` trực tiếp: dùng `RealtimeSender` (`common/realtime`):
  `toUser(userId, RealtimeDestinations.NOTIFICATIONS | SUPPORT, payload)` và `toStaff(destination, payload)`. Gửi lỗi chỉ được ghi log
  (không ném ra), nên gọi thoải mái trong listener `AFTER_COMMIT`; kênh sai là lỗi lập trình (`IllegalArgumentException`).
- Chat: tin của khách → `toStaff(SUPPORT, ...)` tới mọi nhân viên (EMPLOYEE, ADMIN còn hoạt động) **đang kết nối** (`/user/{staffId}/queue/support`);
  tin của nhân viên → `toUser(customerId, SUPPORT, ...)` và `toStaffExcept(staffId, SUPPORT, ...)` (các nhân viên còn lại, trừ người gửi).
- Xác thực JWT ở khung CONNECT (`StompAuthChannelInterceptor`) dùng chung `TokenAuthenticator` với `JwtAuthenticationFilter`.

## Job định kỳ
- `OrderTimeoutJob` (BR-3) — đăng ký bằng `SchedulingConfigurer`, chu kỳ `app.order.timeout-job-delay` (60s).
- `PromotionAnnouncementJob` (D-45, module `promotion`) — chu kỳ `app.notification.announce-job-delay` (60s): công bố voucher công
  khai / chiến dịch vừa tới `start_date` (khoá `FOR UPDATE SKIP LOCKED`, gán `announced_at`, phát sự kiện; lô 50, lần đầu sau một
  chu kỳ). Test tích hợp đặt chu kỳ 1h trong `PostgresTestContainerConfig` (context được cache vẫn chạy job trên DB dùng chung) và
  gọi `announceDue()` trực tiếp.
- Bật `@EnableScheduling`, `@EnableAsync` trong `common/config/AsyncSchedulingConfig`.

## Frontend — hai root layout, phân theo feature (D-18)
```
src/app/
  (store)/                 ROOT LAYOUT storefront: <html> + CSS styles/shop/*
    layout.tsx
    (shop)/                trang cửa hàng (/, /products, /cart...) — mỗi nhóm bọc ShopFrame
    (auth)/                /login, /register, /verify-email, /forgot-password, /reset-password
    account/               /account/*
    [...notFound]/         URL không khớp → 404 storefront
    not-found.tsx
  (admin)/                 ROOT LAYOUT admin: <html> + AntdRegistry + AdminProviders (antd, vi_VN)
    layout.tsx
    admin/
      layout.tsx           AdminShell: sidebar theo role, header, chặn khách hàng
      page.tsx             /admin dashboard
      [...slug]/           trang có trong menu nhưng chưa làm → "đang xây dựng"
      <module>/...         categories, products, combos, inventory, vouchers, campaigns, articles,
                           orders, returns, users, support
  api/                     route handler (mock — xoá khi nối API)
src/components/ui/                 component storefront dùng chung (Button, Modal, Toast, PriceTag...)
src/components/features/<module>/  component storefront theo module (catalog, product, cart, account...)
src/components/admin/              component admin (antd)
  shell/                           AdminShell, AdminSession, AdminProviders, RequireRole
  common/                          dùng chung giữa các trang admin (DataTablePage, ImageUpload...)
  <module>/                        component admin theo module (catalog, combo, inventory...)
src/services/                      gọi API theo module — DÙNG CHUNG storefront + admin
  admin/                           service cho /api/admin/**
src/types/api/                     kiểu khớp docs/02-ky-thuat/api.md (common.ts, catalog.ts, ...)
src/hooks/                         hook dùng chung (useAdminSession, useAuth, useCart...)
src/lib/                           tiện ích (lib/admin/nav.ts: menu + quyền admin; errorMessages...)
src/styles/shop/                   CSS storefront (chỉ nạp ở (store)/layout)
src/styles/admin/                  CSS bổ sung cho admin (chỉ nạp ở (admin)/layout)
src/mocks/                         dữ liệu mock — xoá dần khi nối API
```
- Không import chéo giao diện: `components/features/*` không dùng antd, `components/admin/*` không
  dùng CSS storefront. `services/`, `types/`, `hooks/`, `lib/` dùng chung.
- Link giữa storefront và admin dùng `<a href>` (không `next/link`) vì đổi root layout luôn tải lại trang.
- Menu và quyền từng trang admin khai báo một chỗ: `src/lib/admin/nav.ts` (khớp `pages.md`). Trang chỉ
  dành ADMIN bọc `<RequireRole roles={ADMIN_ONLY}>`; backend vẫn luôn kiểm tra quyền.
- SSR cho trang public (sản phẩm, combo, bài viết, danh mục) — gọi API server-side.
- CSR cho giỏ hàng, thanh toán, tài khoản, admin.
- Trạng thái đăng nhập/giỏ hàng: React context (`ShopProvider` hiện tại sẽ chuyển sang gọi API).
