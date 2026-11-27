# Plan: Thông báo in-app realtime theo role (FR-22)

- **Ngày tạo:** 2026-11-27
- **Người/agent phụ trách:** Người 4 (Claude Code hỗ trợ), nhánh `feature/notification` (tách từ `origin/dev` @ `93a2a44`)
- **Tier:** 2
- **Module backend liên quan:** `notification` (mới, chính); phát sự kiện thêm ở `order` (+`order.returns`), `review`, `inventory`,
  `promotion`, `favorite` (chưa có); đọc qua Service của `user`, `review`, `favorite`; dùng `common/realtime`
- **Module/feature frontend liên quan:** `features/notification` (mới), `features/layout/Header`, `components/admin/shell/AdminShell`,
  `features/support` (đánh dấu đã đọc khi mở chat), trang admin đơn / trả hàng (thông điệp "đã có người xử lý")

## Mục tiêu
Mỗi người dùng đăng nhập nhận thông báo in-app (chuông + trang danh sách) và nhận ngay qua WebSocket:
- **Customer:** đơn đổi trạng thái, kết quả trả hàng, nhân viên trả lời chat, voucher công khai mới, sản phẩm yêu thích giảm giá,
  sản phẩm yêu thích có hàng trở lại.
- **Staff (EMPLOYEE) và ADMIN (giống nhau):** đơn COD mới, đơn VNPay đã thanh toán, yêu cầu trả hàng mới, khách nhắn tin hỗ trợ,
  sắp hết hàng, đánh giá chứa từ nhạy cảm cần xem.
- Thông báo **cần thao tác** của nhân viên tự chuyển sang "Đã xử lý bởi X lúc …" ở mọi nhân viên khi một người đã làm xong việc đó.

## Quyết định đã chốt với người dùng (2026-11-26 → 27)
| # | Nội dung | Ghi vào |
|---|---|---|
| 1 | Danh sách loại thông báo theo role như mục "Danh mục loại thông báo" bên dưới; Admin nhận đúng như Staff | D-42 |
| 2 | Lưu **fan-out**: mỗi người nhận một dòng `notifications` (giữ schema `user_id`, không thêm bảng) | D-42 |
| 3 | Thông báo cần thao tác có `resolved_at`/`resolved_by`; xử lý xong → đánh dấu mọi dòng cùng đối tượng + đẩy WS `RESOLVED` | D-43 |
| 4 | Thông báo xảy ra nhiều lần được **gộp** vào một dòng đang mở (`group_key` + `event_count`) | D-43 |
| 5 | Tạo thông báo bằng domain event + `@TransactionalEventListener(AFTER_COMMIT)` (giữ architecture.md); không outbox | — |
| 6 | Kênh: WebSocket đẩy + REST đồng bộ lại (giữ api.md 4.9) | — |
| 7 | Ngưỡng sắp hết hàng dùng **chung** `app.inventory.low-stock-threshold` (đã có, mặc định 5; cùng định nghĩa với bộ lọc tồn kho) | D-44 |

## Câu hỏi đã chốt (người dùng đồng ý cả 3 mặc định, 2026-11-27)
- **Q1 — Favorite chưa có code** (bảng `favorites` 3.18 chưa có migration, chưa có module). Đề xuất: làm plan riêng `favorite.md`
  (bảng + API thêm/bỏ/danh sách + nút tim) trước Phần 7; nhánh này làm Phần 0–6 trước. *Mặc định nếu không trả lời: như đề xuất.*
- **Q2 — Email (FR-23)** không nằm trong plan này (để task N4-05 riêng, chỉ gửi cho khách). *Mặc định: ngoài phạm vi.*
- **Q3 — Sắp hết hàng theo biến thể:** plan theo dõi ở mức **sản phẩm (tổng) và combo**; chưa báo riêng từng màu/size.
  *Mặc định: mức sản phẩm/combo.*

## Phạm vi (trong/ngoài)
- **Trong phạm vi:** bảng `notifications` + cột phụ; `NotificationService` (tạo, gộp, đánh dấu xử lý, đọc); 3 endpoint REST có sẵn
  trong api.md (mở rộng field + bộ lọc); đẩy WS kiểu phong bì; listener cho 13 loại thông báo; job công bố voucher / chiến dịch;
  frontend chuông + trang `/account/notifications` + `/admin/notifications` + toast.
- **Ngoài phạm vi:** email (Q2); thông báo đẩy ngoài trình duyệt (Web Push); tuỳ chỉnh "tắt loại thông báo"; xoá thông báo; dọn
  thông báo cũ tự động; gộp kết nối STOMP của chat và thông báo thành một (để sau — xem "Rủi ro"); báo tồn kho theo biến thể (Q3);
  chạy nhiều instance backend (simple broker trong bộ nhớ, đã biết).

## Danh mục loại thông báo

Quy ước: **Chi tiết** = gắn một đối tượng, nội dung đủ để quyết định; **Gộp** = một dòng mở cho một nhóm, sự kiện mới cập nhật dòng
đó (`event_count + 1`, nội dung mới nhất, `created_at = now()`, `is_read = false`); **Cần xử lý** = có `resolved_*`.

### Customer
| `type` | Khi nào (nguồn sự kiện) | Mức | title / content mẫu | `link` | `ref_type` / `group_key` |
|---|---|---|---|---|---|
| `ORDER_STATUS` | `OrderStatusChangedEvent` sang CONFIRMED, SHIPPING, COMPLETED, CANCELLED, PAYMENT_FAILED; **bỏ qua** lúc tạo (PENDING), RETURNED (đã có `RETURN_STATUS`) và khi chính khách là người đổi (khách tự huỷ) | Chi tiết | "Đơn #CV108 đang được giao" / "Đơn vị vận chuyển đã nhận hàng." · huỷ: kèm ghi chú | `/account/orders/{id}` | `ORDER` / — |
| `RETURN_STATUS` | `ReturnRequestProcessedEvent` (mới) | Chi tiết | "Yêu cầu trả hàng đơn #CV108 đã được duyệt" / số tiền hoàn · từ chối: lý do | `/account/orders/{orderId}` | `RETURN` / — |
| `SUPPORT_MESSAGE` | `SupportMessageSentEvent` có `isFromStaff` | Gộp | "Nhân viên đã trả lời bạn" / 100 ký tự đầu tin mới nhất (+ "và N tin khác") | `/account/support` | `CONVERSATION` / `SUPPORT:{customerId}` — đóng khi đọc |
| `VOUCHER_NEW` | `VoucherAnnouncedEvent` (mới, từ job) | Chung | "Mã mới CAMP50: giảm 50.000đ" / "Đơn từ 500.000đ · HSD 31/12" | `/promotions` | `VOUCHER` / — |
| `FAVORITE_ON_SALE` | `CampaignStartedEvent` (mới, từ job) | Chi tiết nếu 1 món, gộp nếu nhiều | "Lều X giảm 20%" / "2.000.000đ → 1.600.000đ" · nhiều: "3 sản phẩm bạn yêu thích đang giảm giá trong *Tên chiến dịch*" | trang sản phẩm · nhiều: `/promotions/{slug}` | `PRODUCT` hoặc `CAMPAIGN` / — |
| `FAVORITE_BACK_IN_STOCK` | `StockLevelsChangedEvent` (mới) có khả dụng **trước ≤ 0 và sau > 0** | Chi tiết, gộp theo mặt hàng tới khi đọc | "Lều X đã có hàng trở lại" | `/products/{slug}` · `/combos/{slug}` | `PRODUCT`/`COMBO` / `BACK_IN_STOCK:{type}:{id}` — đóng khi đọc |

### Staff + Admin (gửi cho mọi EMPLOYEE, ADMIN đang hoạt động — `toStaff`)
| `type` | Khi nào | Mức | title / content mẫu | `link` | `ref_type` / `group_key` | Đã xử lý khi |
|---|---|---|---|---|---|---|
| `ORDER_NEW` | Đơn **COD** tạo xong (`OrderStatusChangedEvent` old = null, phương thức COD) | Chi tiết ngắn | "Đơn mới #CV108" / "1.250.000đ · COD · 3 sản phẩm" | `/admin/orders/{id}` | `ORDER` / — | Đơn rời PENDING (CONFIRMED / CANCELLED) |
| `ORDER_PAID` | Đơn VNPay PENDING → CONFIRMED bởi System (IPN) | Chi tiết ngắn | "Đơn #CV109 đã thanh toán VNPay" / "Cần bàn giao vận chuyển" | `/admin/orders/{id}` | `ORDER` / — | Đơn CONFIRMED → SHIPPING |
| `RETURN_NEW` | `ReturnRequestCreatedEvent` (mới) | Chi tiết | "Yêu cầu trả hàng đơn #CV108" / lý do (nhãn) | `/admin/returns/{id}` | `RETURN` / — | Yêu cầu APPROVED / REJECTED |
| `SUPPORT_NEW` | `SupportMessageSentEvent` của khách | Gộp theo khách | "Nguyễn A đã nhắn 3 tin" / trích tin mới nhất | `/admin/support?customer={id}` (route chat admin hiện có) | `CONVERSATION` / `SUPPORT_NEW:{customerId}` | Một nhân viên bất kỳ trả lời khách đó |
| `LOW_STOCK` | `StockLevelsChangedEvent`: khả dụng **sau ≤ ngưỡng** (gồm 0 → chữ "hết hàng") | Chi tiết, gộp theo mặt hàng | "Bếp gas Y sắp hết hàng" / "Còn 3 khả dụng (ngưỡng 5)" | `/admin/inventory?q=…` | `PRODUCT`/`COMBO` / `LOW_STOCK:{type}:{id}` | Khả dụng **> ngưỡng** (resolved_by = người nhập hàng; System nếu do huỷ đơn) |
| `REVIEW_FLAGGED` | `ReviewFlaggedEvent` (mới): đánh giá tạo / sửa bị gắn SENSITIVE | Chung, gộp một dòng | "Có 4 đánh giá cần kiểm duyệt" | `/admin/reviews?status=SENSITIVE` | `REVIEW_QUEUE` / `REVIEW_FLAGGED` | Số đánh giá đang chờ (đúng định nghĩa bộ lọc `status=SENSITIVE`) về 0 |

Quy tắc chung: **không gửi thông báo cho chính người gây ra sự kiện** (actorId); nhân viên xử lý nhận kết quả qua phản hồi REST.

## Thiết kế dữ liệu — `V17__notifications.sql` (dự kiến)
Số V: cao nhất trên mọi nhánh `origin` hiện là V16 (2026-11-27) → V17; kiểm lại ngay trước khi tạo file và trước khi mở PR.

```sql
-- V17 (N4-03, D-42, D-43): thông báo in-app — docs/02-ky-thuat/database.md mục 3.20.
CREATE TABLE notifications (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,  -- người nhận
    type        VARCHAR(30)   NOT NULL,
    title       VARCHAR(255)  NOT NULL,
    content     VARCHAR(1000) NOT NULL,
    link        VARCHAR(500),
    ref_type    VARCHAR(20),                 -- đối tượng liên quan, dùng để đánh dấu đã xử lý
    ref_id      BIGINT,
    group_key   VARCHAR(100),                -- khác NULL = dòng gộp đang mở; đóng nhóm = set NULL
    event_count INT           NOT NULL DEFAULT 1,
    is_read     BOOLEAN       NOT NULL DEFAULT FALSE,
    resolved_at TIMESTAMPTZ,
    resolved_by BIGINT        REFERENCES users (id) ON DELETE SET NULL,  -- NULL + resolved_at = System
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),  -- thời điểm sự kiện MỚI NHẤT (dòng gộp cập nhật lại)
    CONSTRAINT chk_notifications_type CHECK (type IN ('ORDER_STATUS','RETURN_STATUS','SUPPORT_MESSAGE','VOUCHER_NEW',
        'FAVORITE_ON_SALE','FAVORITE_BACK_IN_STOCK','ORDER_NEW','ORDER_PAID','RETURN_NEW','SUPPORT_NEW','LOW_STOCK',
        'REVIEW_FLAGGED')),
    CONSTRAINT chk_notifications_ref_type CHECK (ref_type IS NULL OR ref_type IN ('ORDER','RETURN','CONVERSATION','PRODUCT',
        'COMBO','VOUCHER','CAMPAIGN','REVIEW_QUEUE')),
    CONSTRAINT chk_notifications_event_count CHECK (event_count >= 1),
    CONSTRAINT chk_notifications_resolved CHECK (resolved_by IS NULL OR resolved_at IS NOT NULL)
);

CREATE INDEX idx_notifications_user_id_is_read ON notifications (user_id, is_read, created_at DESC);  -- mục 3.23
CREATE INDEX idx_notifications_open_ref ON notifications (type, ref_type, ref_id) WHERE resolved_at IS NULL;
CREATE UNIQUE INDEX uq_notifications_user_group ON notifications (user_id, group_key) WHERE group_key IS NOT NULL;

-- Job công bố (D-45): đánh dấu dữ liệu đã có là "đã công bố" để lần chạy đầu không gửi hàng loạt mã/chiến dịch cũ.
ALTER TABLE vouchers ADD COLUMN announced_at TIMESTAMPTZ;
UPDATE vouchers SET announced_at = now();
ALTER TABLE discount_campaigns ADD COLUMN announced_at TIMESTAMPTZ;
UPDATE discount_campaigns SET announced_at = now();
```

- Dòng gộp ghi bằng **một câu** `INSERT … ON CONFLICT (user_id, group_key) WHERE group_key IS NOT NULL DO UPDATE SET
  event_count = notifications.event_count + 1, content = EXCLUDED.content, title = EXCLUDED.title, created_at = EXCLUDED.created_at,
  is_read = FALSE` → hai sự kiện cùng lúc không tạo hai dòng (unique một phần là chốt cuối).
- Đóng nhóm: loại **không cần xử lý** (`SUPPORT_MESSAGE`) đóng khi đọc; loại **cần xử lý** đóng khi resolved (đọc không đóng — tin
  mới của khách vẫn dồn vào cùng dòng và làm nó "chưa đọc" lại).
- `unreadCount` = `is_read = false AND resolved_at IS NULL` của chính người gọi.

## Thiết kế API (sửa api.md 4.7 và 4.9)
| Method | Path | Quyền | Body → Response |
|---|---|---|---|
| GET | `/notifications` | User | `?isRead=true\|false&resolved=true\|false&page&limit` → `{items: PageResponse<Notification>, unreadCount}` (giữ wrapper hiện có), mới nhất trước |
| PATCH | `/notifications/{id}/read` | User (chủ, khác chủ → 404) | → `{unreadCount}` |
| PATCH | `/notifications/read-all` | User | → `{updated, unreadCount}` |

```ts
type NotificationType = "ORDER_STATUS" | "RETURN_STATUS" | "SUPPORT_MESSAGE" | "VOUCHER_NEW" | "FAVORITE_ON_SALE"
  | "FAVORITE_BACK_IN_STOCK" | "ORDER_NEW" | "ORDER_PAID" | "RETURN_NEW" | "SUPPORT_NEW" | "LOW_STOCK" | "REVIEW_FLAGGED";
interface Notification {
  id: number; type: NotificationType; title: string; content: string; link: string | null;
  eventCount: number; isRead: boolean; createdAt: string;
  actionable: boolean;                       // loại cần xử lý (chỉ của nhân viên)
  resolvedAt: string | null; resolvedBy: { id: number; fullName: string } | null;  // resolvedAt có, resolvedBy null = Hệ thống
}
// Kênh /user/queue/notifications — đổi từ Notification thô sang phong bì:
type NotificationPush =
  | { event: "CREATED" | "UPDATED"; notification: Notification; unreadCount: number }   // UPDATED = dòng gộp nhận thêm sự kiện
  | { event: "RESOLVED"; ids: number[]; resolvedAt: string; resolvedBy: {id:number; fullName:string} | null; unreadCount: number };
```
Không thêm mã lỗi mới.

## Thiết kế backend
- `notification/`: `Notification` (entity, chỉ đọc + `markRead`), `NotificationType` (enum: `actionable()`, `audience()`),
  `NotificationRefType`, `NotificationRepository` (JPA: `findByIdAndUserId`, `countUnread…`, Specification cho danh sách),
  `NotificationWriter` (**JDBC** — đã làm ở Phần 1 thay cho native query trong repository: `insert` / `upsertGroup` một câu cho mọi
  người nhận qua `unnest(bigint[])`, `ON CONFLICT … RETURNING id, user_id, (xmax = 0)`; `resolveOpen … RETURNING`; `unreadCounts`;
  `markAllRead`), `NotificationMessage` (record nội dung + `groupKey`), `NotificationMessages` (dựng nội dung 12 loại),
  `NotificationService` (`notifyUser`, `notifyStaff(exceptId)`, `notifyAllCustomers`, `notifyUsers` theo lô, `resolve`; ghi trong
  `TransactionTemplate` `REQUIRES_NEW`, đẩy WS SAU khi transaction đó commit), `NotificationController`, `dto/NotificationResponse`,
  `dto/NotificationPush`.
- `notification/listener/`: `OrderNotificationListener`, `ReturnNotificationListener`, `SupportNotificationListener`,
  `StockNotificationListener`, `ReviewNotificationListener`, `PromotionNotificationListener`. Mỗi listener
  `@TransactionalEventListener(AFTER_COMMIT)`; fan-out lớn (voucher, chiến dịch, có hàng lại) thêm `@Async`. Lỗi trong listener chỉ log
  (không làm hỏng nghiệp vụ đã commit).
- Nội dung (title/content/link) dựng ở một chỗ: `NotificationMessages` (hàm thuần, dễ unit test), định dạng tiền `1.250.000đ`.
- **Sự kiện mới** (record, module phát sự kiện sở hữu, không import gì của `notification`):
  - `order.returns.ReturnRequestCreatedEvent(returnId, orderId, orderCode, customerId, reasonLabel)` — phát cuối `ReturnService.create`.
  - `order.returns.ReturnRequestProcessedEvent(returnId, orderId, orderCode, customerId, status, note, refundAmount, staffId)` —
    phát cuối `ReturnService.process`.
  - `review.ReviewFlaggedEvent(reviewId)` — `ReviewService.create/update` khi kết quả kiểm duyệt SENSITIVE.
  - `review.ReviewModeratedEvent(reviewId, staffId)` — `AdminReviewService.hide/clearSensitive/delete` và khi khách xoá đánh giá
    SENSITIVE (hàng chờ giảm).
  - `inventory.StockLevelsChangedEvent(List<Level(itemType, itemId, name, slug, availableBefore, availableAfter)>, actorId)` —
    `InventoryService` chụp khả dụng lúc `lock(...)` và phát ở cuối `reserve`, `release`, `restockReturn`;
    `AdminInventoryService.adjust`. Chỉ đưa vào event các mặt hàng có khả dụng thay đổi.
  - `promotion.VoucherAnnouncedEvent(voucherId, code, discountType, discountValue, maxDiscount, minOrderValue, expiryDate)` và
    `promotion.CampaignStartedEvent(campaignId, name, slug, productIds)` — do `PromotionAnnouncementJob` (mới, module `promotion`)
    phát: mỗi `app.notification.announce-job-delay` (mặc định 60s) tìm voucher `is_public AND is_active AND start_date <= now <
    expiry_date AND used_count < quantity AND announced_at IS NULL` và chiến dịch `is_active AND start_date <= now < end_date AND
    announced_at IS NULL`, set `announced_at` rồi phát event (khoá dòng `FOR UPDATE SKIP LOCKED` để không công bố hai lần).
  - Đã có, dùng lại: `OrderStatusChangedEvent` (cần thêm `paymentMethod`? → KHÔNG: listener tra qua `OrderQueryService`),
    `SupportMessageSentEvent`.
- **Service public cần thêm ở module khác** (không gọi Repository chéo — rules/backend.md):
  `UserService.activeStaffIds()`, `UserService.activeCustomerIds()` (họ tên người xử lý dùng `UserService.profiles` đã có);
  `OrderQueryService.notificationInfo(orderId)` (mã đơn, chủ đơn, phương thức, tổng tiền, số món, ghi chú lần đổi cuối);
  `ReviewQueryService.countPendingSensitive()`; `PricingService` (giá sau chiến dịch của sản phẩm); `FavoriteService.userIdsFor(
  products, combos)` (Phần 7).
- Cấu hình mới: `app.notification.announce-job-delay` (60s), `app.notification.fan-out-batch-size` (500) → `AppProperties`,
  `tech-stack.md`.

## File sẽ tạo/sửa
| File | Tạo mới / Sửa | Ghi chú |
|---|---|---|
| `backend/src/main/resources/db/migration/V17__notifications.sql` | Tạo | Như trên |
| `backend/.../notification/**` | Tạo | entity, enum, repository, service, controller, dto, listener, messages |
| `backend/.../order/returns/ReturnService.java` + 2 event | Sửa / Tạo | Module Người 1 — báo trước |
| `backend/.../inventory/InventoryService.java`, `inventory/admin/AdminInventoryService.java`, `inventory/StockLevelsChangedEvent.java` | Sửa / Tạo | Module Người 2 — báo trước |
| `backend/.../combo/admin/AdminComboService.java` | Sửa | đóng gói / tháo phát `StockLevelsChangedEvent` (thêm ở Phần 5) |
| `backend/.../review/ReviewService.java`, `review/admin/AdminReviewService.java`, `AdminReviewController.java`, `ReviewQueryService.java`, `ReviewRepository.java`, `Review.java` + 2 event | Sửa / Tạo | |
| `backend/.../promotion/PromotionAnnouncementJob.java`, `Voucher.java`, `DiscountCampaign.java`, repository + 2 event | Tạo / Sửa | cột `announced_at` |
| `backend/.../user/UserService.java`, `order/OrderQueryService.java` | Sửa | thêm hàm đọc |
| `backend/.../common/config/AppProperties.java`, `application*.yml(.example)` | Sửa | `app.notification.*` |
| `backend/.../auth/SecurityConfig.java` | Kiểm | `/api/notifications/**` cần đăng nhập (mọi role) |
| `backend/.../notification/listener/FavoriteNotificationListener.java` | Tạo | Phần 7 (module `favorite`: plan `favorite.md`) |
| `backend/src/test/.../notification/**` | Tạo | xem "Cách test" |
| `frontend/src/types/api/notification.ts`, `services/notificationService.ts` | Tạo | |
| `frontend/src/components/features/notification/**` | Tạo | `useNotificationSocket`, `NotificationProvider`, `NotificationBell`, `NotificationList`, `NotificationItem`, toast |
| `frontend/src/app/(store)/account/notifications/page.tsx`, `app/(admin)/admin/notifications/page.tsx` | Tạo | |
| `frontend/src/components/features/layout/Header.tsx`, `components/admin/shell/AdminShell.tsx`, `app/(store)/layout.tsx` | Sửa | gắn Provider + chuông, mục "Thông báo" trong menu tài khoản / nav admin |
| `frontend/src/components/features/support/*` | Sửa nhỏ | mở `/account/support` → đánh dấu `SUPPORT_MESSAGE` đã đọc (`SupportChat.tsx` thêm 1 hook) |
| `frontend/src/services/apiClient.ts` | Sửa | thêm `getWithPagination` (Phần 8) |
| `frontend/src/components/features/layout/ShopFrame.tsx`, `features/notification/StoreNotifications.tsx`, `features/account/AccountPage.tsx`, `app/(store)/account/notifications/page.tsx` | Sửa / Tạo | Provider storefront, tab "Thông báo" |
| `frontend/src/components/admin/shell/AdminNotificationToaster.tsx`, `lib/admin/nav.ts`, `lib/notification.ts`, `styles/notification.css`, `app/(store)/layout.tsx`, `app/(admin)/layout.tsx` | Tạo / Sửa | toast admin, mục nav, hàm hiển thị, CSS chung |
| `frontend/src/components/admin/order/OrderDetailView.tsx`, `admin/returns/ReturnDetailView.tsx` | Sửa nhỏ | câu "đã có người xử lý" (module Người 1 — báo) |
| `docs/02-ky-thuat/{database,api,architecture,tech-stack}.md`, `docs/03-san-pham/{features,pages,user-flows,use-cases}.md`, `docs/99-ghi-chu/decisions.md` | Sửa | Bước 0 |
| `.agents/plans/active/backend-roadmap.md`, `.agents/progress.md` | Sửa | tick F-09 (phần notifications), N4-03, N4-04 |

## Thứ tự thực hiện
Làm và commit theo từng phần trên `feature/notification` (người dùng tự add/commit/push). Đề xuất 2 PR vào `dev` nếu PR lớn:
PR 1 = Phần 0–5 (backend lõi + sự kiện đơn/trả hàng/chat/tồn kho/review + frontend tối thiểu ở Phần 8 có thể kéo lên trước),
PR 2 = Phần 6–7 (job khuyến mãi, favorite).

0. **Docs trước** (điểm dừng — người dùng duyệt).
1. **Schema + lõi:** V17, entity, repository (gồm upsert gộp, resolve), `NotificationService`, đẩy WS phong bì.
2. **REST:** 3 endpoint + bộ lọc + `unreadCount`.
3. **Đơn hàng + trả hàng:** `ORDER_STATUS`, `ORDER_NEW`, `ORDER_PAID`, `RETURN_NEW`, `RETURN_STATUS` + resolve.
4. **Chat:** `SUPPORT_MESSAGE`, `SUPPORT_NEW` (gộp) + resolve.
5. **Tồn kho + đánh giá:** `StockLevelsChangedEvent`, `LOW_STOCK`; `REVIEW_FLAGGED` + resolve.
6. **Khuyến mãi:** `PromotionAnnouncementJob`, `VOUCHER_NEW`.
7. **Yêu thích** (sau plan `favorite.md`): `FAVORITE_ON_SALE`, `FAVORITE_BACK_IN_STOCK`.
8. **Frontend:** service, socket, provider, chuông, 2 trang, toast, xử lý "đã có người xử lý".
9. **Hoàn tất:** lint/test, thử tay nhiều tab (2 nhân viên + 1 khách), docs trạng thái, progress, kiểm lại số V17.

## Ảnh hưởng tới hệ thống hiện có
- **Không đổi** máy trạng thái đơn / trả hàng, giá, voucher, khoá tồn kho. Chỉ **thêm** `publishEvent` ở cuối các hàm đang có
  transaction — listener chạy AFTER_COMMIT nên rollback → không có thông báo.
- `InventoryService` (Người 2) thêm việc chụp khả dụng trước/sau trong các hàm đổi kho: không đổi thứ tự khoá, không thêm truy vấn
  (dùng entity đã khoá). Báo Người 2 trước khi sửa.
- `ReturnService` (Người 1): chỉ thêm 2 dòng `publishEvent`. Báo Người 1.
- `vouchers`, `discount_campaigns` thêm cột `announced_at` (nullable) — entity hiện có vẫn `validate` được sau khi thêm field.
- Kênh `/user/queue/notifications` đổi payload sang phong bì — chưa client nào dùng.
- Mỗi đơn đặt hàng thêm 1 listener nhẹ (so ngưỡng tồn kho). Fan-out voucher tới toàn bộ khách: insert theo lô
  `fan-out-batch-size`, `@Async`, không chặn job.
- Hai nhân viên xử lý cùng lúc: đã chặn ở tầng nghiệp vụ (`INVALID_STATUS_TRANSITION` cho đơn và trả hàng); review: ẩn/đánh dấu an
  toàn là idempotent nên không lỗi. Frontend hiện câu "Đã có người xử lý" + tải lại.

## Rủi ro / lưu ý
- Hai kết nối STOMP trên cùng một tab (chat + thông báo) ở trang `/account/support` và `/admin/support`: chấp nhận ở lần này
  (đơn giản, không sửa hook của Người 3); hướng sau: một `RealtimeProvider` dùng chung.
- Email xác thực chưa xong / nhân viên bị khoá: `activeStaffIds()` chỉ lấy tài khoản hoạt động (giống `toStaff`).
- Nhân viên tạo SAU sự kiện không thấy thông báo cũ (đánh đổi của fan-out, ghi trong D-42).
- Listener async chạy trên pool `AsyncSchedulingConfig` — kiểm tra kích thước pool đủ cho GHN + thông báo.

## Cách test
- **Unit:** `NotificationMessagesTest` (title/content/link từng loại, định dạng tiền, cắt 100 ký tự, gộp "và N tin khác");
  `NotificationTypeTest` (`actionable`, `audience`).
- **Schema:** type / ref_type lạ, `event_count = 0`, `resolved_by` không có `resolved_at` bị chặn; hai dòng cùng `(user, group_key)`
  bị chặn, `group_key` NULL thì không.
- **Integration (Testcontainers):**
  - `NotificationApiIntegrationTest`: chỉ thấy của mình; `isRead`/`resolved` lọc đúng; `read` của người khác → 404; `read-all` chỉ đánh
    dấu của mình và trả `updated` đúng; `unreadCount` giảm sau đọc 1 cái và không đếm dòng đã xử lý; không token → 401.
  - `OrderNotificationIntegrationTest`: đặt COD → mọi nhân viên có `ORDER_NEW`, khách không có; đặt VNPay → chưa ai có; IPN thành
    công → `ORDER_PAID` cho nhân viên + `ORDER_STATUS` CONFIRMED cho khách; NV A xác nhận COD → `ORDER_NEW` của A và B đều
    `resolvedBy = A`, `unreadCount` của B giảm; khách tự huỷ → khách không nhận `ORDER_STATUS`; job huỷ quá hạn → resolve với
    `resolvedBy = null`; **transaction rollback** (vd. hết hàng lúc tạo đơn) → không có dòng nào.
  - `ReturnNotificationIntegrationTest`: gửi yêu cầu → `RETURN_NEW`; duyệt/từ chối → `RETURN_STATUS` cho khách (từ chối có lý do) +
    resolve; RETURNED không sinh thêm `ORDER_STATUS`.
  - `SupportNotificationIntegrationTest`: khách gửi 3 tin → mỗi NV đúng 1 dòng `SUPPORT_NEW`, `eventCount = 3`; NV trả lời → resolve
    cho mọi NV + khách 1 dòng `SUPPORT_MESSAGE`; khách đọc rồi NV trả lời tiếp → dòng MỚI; gửi song song 2 tin → vẫn 1 dòng.
  - `StockNotificationIntegrationTest`: khả dụng 6 → 5 (ngưỡng 5) → `LOW_STOCK`; tiếp 5 → 2 → vẫn 1 dòng (nội dung "Còn 2");
    nhập lên 10 → resolve `resolvedBy` = người nhập; huỷ đơn làm khả dụng vượt ngưỡng → resolve bởi System.
  - `ReviewNotificationIntegrationTest`: 2 đánh giá SENSITIVE → 1 dòng "2 đánh giá"; ẩn 1 → nội dung "1"; đánh dấu an toàn cái còn lại
    → resolve.
  - `PromotionAnnouncementJobTest`: voucher công khai bắt đầu trong tương lai → chưa gửi; tới giờ → mỗi khách 1 dòng, chạy job lần 2
    không gửi lại; voucher ẩn / tắt → không gửi; dữ liệu seed (`announced_at` đã set) → không gửi.
  - `NotificationRealtimeIntegrationTest` (kiểu `RealtimeIntegrationTest`): client STOMP của khách nhận `CREATED`; client NV B nhận
    `RESOLVED` khi NV A xử lý; NV A không nhận lại.
- **Frontend (thủ công, 3 cửa sổ: khách, NV A, NV B):** badge, dropdown, toast, bấm đi đúng `link`, lọc trang danh sách, "đánh dấu
  tất cả", dòng đã xử lý mờ kèm "Đã xử lý bởi …", mất mạng rồi có lại → đồng bộ lại, trạng thái loading/empty/error, màn hình 390px.

## Trạng thái
- [x] Đang làm
- [x] Đã test (backend 661 xanh, E2E API + WS trên stack local; giao diện trình duyệt chờ người dùng thử)
- [x] Đã cập nhật docs/
- [ ] Đã mở PR — việc của người dùng; kiểm lại V17 trên `origin` ngay trước khi mở

## Danh sách task

**Quy ước làm việc:** mọi task làm trên nhánh `feature/notification`. KHÔNG merge, rebase, push hay sửa nhánh khác. Chưa
`git add`/commit/push khi người dùng chưa yêu cầu. Mỗi task xong: tick ô. Mỗi "điểm dừng" = người dùng kiểm tra trước khi sang phần
sau. Cột "Roadmap": **F-09** (phần `notifications`), **N4-03** (lõi), **N4-04** (listener).

### Bước 0 — Contract + docs (điểm dừng 0)
| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] NT-01 | Docs | `decisions.md`: D-42 (danh mục loại theo role, fan-out, Admin = Staff), D-43 (resolved + gộp `group_key`), D-44 (ngưỡng chung, mức sản phẩm/combo), D-45 (job công bố + `announced_at`, không gửi dữ liệu cũ) | — | 4 quyết định có Lý do + Đánh đổi |
| - [x] NT-02 | Docs | `database.md` 3.20 (cột mới, CHECK, ghi chú gộp/đóng nhóm), 3.14/3.15 thêm `announced_at`, 3.23 index mới, số migration V17 | NT-01 | Khớp mục "Thiết kế dữ liệu" |
| - [x] NT-03 | Docs | `api.md` mục 1 `NotificationType`, kiểu `Notification`, 4.7 (bộ lọc `resolved`, response `read`/`read-all`), 4.9 phong bì `NotificationPush`; bỏ dòng "chưa làm: chờ N4-03" ở 4.8 | NT-01 | Khớp mục "Thiết kế API" |
| - [x] NT-04 | Docs | `architecture.md` (danh sách event mới, job công bố), `tech-stack.md` (`app.notification.*`), `features.md` FR-22 (loại theo role, đã xử lý), `pages.md` (`/account/notifications`, thêm `/admin/notifications`, chuông ở header + admin shell), `user-flows.md`, `use-cases.md` | NT-02, NT-03 | **Người dùng duyệt docs** |

### Phần 1 — Schema + lõi (F-09, N4-03)
| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] NT-05 | BE | Kiểm lại số V trên `origin` (2026-11-27: cao nhất V16); tạo `V17__notifications.sql` (+ index FK `resolved_by`); thêm field `announcedAt` + `markAnnounced` vào `Voucher`, `DiscountCampaign` | NT-02 | Flyway V17 OK, `validate` sạch |
| - [x] NT-06 | BE | `NotificationType` (+`actionable`, `audience`), `NotificationRefType`, `Notification` entity (`markRead` đóng nhóm loại không cần xử lý), `NotificationRepository`, `NotificationWriter` (JDBC: `insert`, `upsertGroup` ON CONFLICT, `resolveOpen … RETURNING`, `unreadCounts`, `markAllRead`) | NT-05 | Compile + IT |
| - [x] NT-07 | Test | `NotificationSchemaTest` 8 test (CHECK type / ref_type / event_count / resolved, unique một phần, FK CASCADE / SET NULL, cột `announced_at`) | NT-06 | IT xanh |
| - [x] NT-08 | BE | `UserService.activeStaffIds/activeCustomerIds`; `AppProperties.notification` + yml; `NotificationService` (`notifyUser`, `notifyStaff(exceptId)`, `notifyAllCustomers`, `notifyUsers` theo lô, `resolve`, `REQUIRES_NEW`), đẩy `NotificationPush` sau commit kèm `unreadCount` từng người; `NotificationServiceIntegrationTest` 8 test (lô + id trùng, bỏ người gây ra / khách / NV bị khoá, gộp + mở lại chưa đọc, đọc đóng nhóm, 8 luồng song song → 1 dòng, resolve đẩy cho người khác, resolve bởi Hệ thống, loại không cần xử lý bị từ chối) | NT-06 | IT xanh |
| - [x] NT-09 | BE | `NotificationMessages` + `NotificationMessagesTest` 14 test | — | Unit xanh — **điểm dừng 1a** (người dùng xem lõi trước REST) |

### Phần 2 — REST (N4-03)
| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] NT-10 | BE | `NotificationController` (`page` ≥ 1, `limit` 1–100 mặc định 20), `NotificationSpecifications` (lọc `isRead`, `resolved`), `NotificationService.list/markRead/markAllRead`, DTO `NotificationListResponse` (`{success, data: {items, unreadCount}, pagination}`), `ReadResponses`; `resolvedBy.fullName` qua `UserService.profiles`. `SecurityConfig` không cần sửa (`/api/notifications` rơi vào `anyRequest().authenticated()`) | NT-08 | Endpoint chạy |
| - [x] NT-11 | Test | `NotificationApiIntegrationTest` 7 test (chỉ của mình + phân trang + JSON đúng contract, lọc `isRead`, lọc `resolved` + `resolvedBy`, `read` của người khác 404 + idempotent, `read-all` chỉ của mình + `updated`, đọc đóng nhóm khách nhưng giữ nhóm nhân viên, 401 / 400). Đột biến: bỏ lọc người nhận → 3 test đỏ; bỏ kiểm chủ khi `read` → đỏ | NT-10 | IT xanh — **điểm dừng 1** |

### Phần 3 — Đơn hàng + trả hàng (N4-04)
| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] NT-12 | BE | `OrderQueryService.notificationInfo` (+ `dto/OrderNotificationInfo`: mã, khách, phương thức, tổng tiền, số món, ghi chú lần đổi cuối); `OrderNotificationListener` (đồng bộ, AFTER_COMMIT, lỗi chỉ log): `ORDER_NEW` khi tạo đơn COD, `ORDER_PAID` khi đơn không COD PENDING → CONFIRMED, resolve `ORDER_NEW` khi rời PENDING / `ORDER_PAID` khi CONFIRMED → SHIPPING (khách tự huỷ → `resolved_by` NULL), `ORDER_STATUS` cho 5 trạng thái trừ khi chính khách đổi | NT-08 | |
| - [x] NT-13 | BE | `ReturnRequestCreatedEvent`, `ReturnRequestProcessedEvent` + `publishEvent` trong `ReturnService.create` (trong `tx.execute`) và `process`; `ReturnNotificationListener` (`RETURN_NEW` cho nhân viên; resolve + `RETURN_STATUS` cho khách) | NT-08 | |
| - [x] NT-14 | Test | Gộp thành một lớp `OrderReturnNotificationIntegrationTest` 9 test (COD → mọi NV, xác nhận → resolve cho cả hai + khách nhận CONFIRMED; VNPay chỉ báo sau IPN, SHIPPING resolve; IPN thất bại chỉ báo khách; khách tự huỷ; NV huỷ có lý do; khách nhận đủ 3 bước; **rollback → không tạo / không resolve**; trả hàng từ chối có lý do; duyệt có tiền hoàn, không thêm ORDER_STATUS RETURNED). Đột biến: bỏ `!byCustomer`, bỏ kiểm COD, đổi sang `@EventListener` thường → đều có test đỏ. Sửa kỳ vọng `SecurityIntegrationTest.anyRoleCanReachUserScopedEndpointsPastSecurity` 404 → 200 (test cũ dựa vào việc `/api/notifications` chưa có controller). Cả bộ 628 test xanh | NT-12, NT-13 | IT xanh |

### Phần 4 — Chat hỗ trợ
| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] NT-15 | BE | `SupportNotificationListener`: tin khách → upsert `SUPPORT_NEW` cho mọi NV (tên khách qua `UserService.profiles`); tin NV → resolve `SUPPORT_NEW` của khách đó (resolvedBy = người gửi) + upsert `SUPPORT_MESSAGE` cho khách | NT-08 | |
| - [x] NT-15b | BE | **Phát sinh (D-46):** test gửi song song làm treo pool (AFTER_COMMIT giữ kết nối gốc + `REQUIRES_NEW` cần kết nối thứ hai). Thêm `NotificationDispatcher` (hàng đợi một luồng, `drain()` cho test); 3 listener (đơn, trả hàng, chat) chuyển sang `dispatcher.submit`. Test luồng nghiệp vụ chờ `drain()` trước khi đọc. Docs: D-46, architecture.md | NT-15 | Hết treo; đột biến "chạy ngay trên luồng gọi" → test song song đỏ |
| - [x] NT-16 | Test | `SupportNotificationIntegrationTest` 5 test (gộp 3 tin / NV, NV trả lời → resolve cho mọi NV + khách nhận, nhóm đóng thì tin sau mở dòng mới, khách đọc thì mở dòng mới, mỗi khách một nhóm, 6 luồng song song → 1 dòng / NV). Đột biến bỏ resolve → 2 test đỏ. Cả bộ: lớp test mới làm vượt 100 kết nối PostgreSQL ("too many clients already" ở `UserRepositoryTest`) → `PostgresTestContainerConfig` đặt `max_connections=200`. Cả bộ 633 test xanh | NT-15 | IT xanh — **điểm dừng 2** |

### Phần 5 — Tồn kho + đánh giá
| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] NT-17 | BE | `StockLevelsChangedEvent` + `inventory.StockLevelSnapshot` (chụp trên entity đã khoá, không thêm truy vấn). Rà xong: `InventoryService.reserve/commit/release/restockReturn`, `AdminInventoryService.adjust` (biến thể + combo), **thêm** `AdminComboService.pack/unpack` (ngoài kế hoạch — đóng gói trừ kho sản phẩm thành phần). `allocateSold` không đổi khả dụng sản phẩm | — | Test inventory cũ vẫn xanh |
| - [x] NT-18 | BE | `StockNotificationListener`: đọc lại khả dụng hiện tại; > ngưỡng → resolve (nhân viên / Hệ thống); ≤ ngưỡng + giảm → báo/gộp (trừ người gây ra); ≤ ngưỡng + tăng → chỉ sửa số. Thêm `NotificationWriter.refreshGroup` / `NotificationService.refreshGroup` (sửa tiêu đề/nội dung dòng gộp đang mở, giữ `is_read`/`event_count`, đẩy `UPDATED`) | NT-17, NT-08 | |
| - [x] NT-19 | BE | `ReviewFlaggedEvent`, `ReviewModeratedEvent(reviewId, staffId, addedToQueue)`, `Review.isPendingModeration`, `ReviewQueryService.countPendingSensitive`; `ReviewNotificationListener` (gắn cờ → báo; bớt hàng chờ → sửa số; về 0 → resolve; hiện lại → báo NV khác). `AdminReviewService.unhide/clearSensitive/delete` nhận thêm `staffId` (controller truyền `@CurrentUserId`) | NT-08 | |
| - [x] NT-20 | Test | `StockNotificationIntegrationTest` 4 test, `ReviewNotificationIntegrationTest` 4 test. Đột biến (luôn báo khi ≤ ngưỡng, luôn báo khi kiểm duyệt, bỏ publish ở đóng gói) → 3 test đỏ. `NotificationSchemaTest` sửa để chỉ đếm dòng của user nó tạo (lớp test khác giờ sinh `LOW_STOCK` thật). Cả bộ 641 test xanh | NT-18, NT-19 | IT xanh — **điểm dừng 3** |

### Phần 6 — Khuyến mãi
| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] NT-21 | BE | `app.notification.*` (đã có từ Phần 1); `PromotionAnnouncementJob` (`SchedulingConfigurer`, lần đầu sau một chu kỳ, lô 50 / transaction, `lockDueForAnnouncement` … `FOR UPDATE SKIP LOCKED` ở `VoucherRepository` + `DiscountCampaignRepository`, gán `announced_at`, phát `VoucherAnnouncedEvent` / `CampaignStartedEvent(productIds)`) | NT-05 | |
| - [x] NT-22 | BE | `PromotionNotificationListener` phần `VOUCHER_NEW` — chạy trên `NotificationDispatcher` (D-46) thay cho `@Async`, `notifyAllCustomers` theo lô | NT-21, NT-08 | |
| - [x] NT-23 | Test | `PromotionAnnouncementJobTest` 5 test (đúng một lần, chạy lại, hẹn giờ, ẩn/tắt/hết lượt/hết hạn/đã đánh dấu như seed, 2 luồng song song, chiến dịch). Đột biến bỏ `SKIP LOCKED` + bỏ `is_public` → 2 test đỏ. `PostgresTestContainerConfig` đặt `announce-job-delay=1h` | NT-22 | IT xanh |

### Phần 7 — Yêu thích (module `favorite` làm trên chính nhánh này — plan `favorite.md`, D-47)
| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] NT-24 | BE | `FavoriteService.customersFavoriting(type, itemIds)` → itemId → khách ĐANG HOẠT ĐỘNG (làm trong `favorite.md` FV-03) | favorite | |
| - [x] NT-25 | BE | `FavoriteNotificationListener.onCampaignStarted` (dispatcher): giá lấy từ `ProductService.activeSummaries` (giá sau chiến dịch — `@Formula`, không cần gọi `PricingService` riêng); chỉ sản phẩm đang bán + rẻ hơn giá gốc; mỗi khách 1 thông báo / chiến dịch (1 món chi tiết, nhiều món gộp); gom người nhận cùng nội dung để ghi theo lô | NT-24, NT-21 | |
| - [x] NT-26 | BE | `FavoriteNotificationListener.onStockLevelsChanged` — **lớp riêng**, không nhét vào `StockNotificationListener` (khác người nhận, khác điều kiện): trước ≤ 0 < sau, đọc lại (đang bán + còn hàng). **Thêm:** `backInStock` có `group_key = BACK_IN_STOCK:{type}:{id}`, đóng khi đọc (kho dao động 0 ↔ 1 không sinh nhiều dòng) — D-43 / D-47 / database.md đã ghi | NT-24, NT-17 | |
| - [x] NT-27 | Test | `FavoriteNotificationIntegrationTest` 4 test (nhập 0 → 3 chỉ tới khách yêu thích đang hoạt động; 3 → 5 không báo; 0 → 1 khi chưa đọc → gộp, đã đọc → dòng mới; sản phẩm ẩn không báo; đóng gói combo 0 → 2 báo người thích combo, thành phần còn hàng thì không; chiến dịch: 1 món chi tiết giá 80.000đ, 2 món gộp + link chiến dịch, sản phẩm ẩn không tính, ngoài chiến dịch / bị khoá / nhân viên không nhận, chạy lại không gửi). `NotificationMessagesTest` kiểm `groupKey`. Đột biến (bỏ "trước ≤ 0", luôn gửi chi tiết) → 3 test đỏ | NT-25, NT-26 | IT xanh |

### Phần 8 — Frontend
| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] NT-28 | FE | `types/api/notification.ts`, `services/notificationService.ts`; `NotificationType` trong `types/api/common.ts` đủ 12 loại; **thêm** `apiClient.getWithPagination` (response `data` là object `{items, unreadCount}` kèm `pagination`, `getPage` chỉ cho mảng) | NT-03 | `tsc` sạch |
| - [x] NT-29 | FE | `useNotificationSocket` (theo mẫu `useSupportSocket`, kênh `/user/queue/notifications`, nối lại → đồng bộ REST); `NotificationProvider` (unreadCount, 10 mục mới nhất, áp `CREATED/UPDATED/RESOLVED`, `markRead`, `markAll`). **Thêm:** `subscribe` (khung WS + sự kiện cục bộ `READ`/`READ_ALL`/`SYNC` để trang danh sách đi cùng chuông), quay lại tab sau 30s → tải lại, `watchType` / `useAutoReadNotifications`. Toast khi `CREATED` **hoặc** `UPDATED` có sự kiện mới (`createdAt` đổi; `refreshGroup` chỉ sửa số thì không). Storefront bật cho CUSTOMER (`StoreNotifications` trong `ShopFrame`), admin bật cho nhân viên (`AdminShell`) | NT-28 | |
| - [x] NT-30 | FE | `NotificationBell` (badge 99+, dropdown, "Đánh dấu tất cả đã đọc", "Xem tất cả", icon theo loại, mục đã xử lý mờ + "Đã xử lý bởi …") gắn vào `Header` (khách) và `AdminShell` (NV); toast khi `CREATED`. Một component HTML thường dùng cho cả hai khu vực, CSS chung `styles/notification.css` (nạp ở cả 2 root layout); toast storefront `NotificationToaster`, admin `AdminNotificationToaster` (antd `notification`). Màn hình ≤ 640px: dropdown / toast bám mép màn hình | NT-29 | |
| - [x] NT-31 | FE | Trang `/account/notifications` và `/admin/notifications` (tab Tất cả / Chưa đọc / Đã đọc; admin thêm "Chưa xử lý"; phân trang; loading/empty/error); mục nav. `/account/notifications` = tab "Thông báo" của `AccountPage` (như `/account/support`); `/admin/notifications` trong nhóm "Tổng quan" của `lib/admin/nav.ts` (STAFF). Chung `NotificationList` | NT-29 | |
| - [x] NT-32 | FE | Mở `/account/support` → đánh dấu đọc `SUPPORT_MESSAGE`; trang admin đơn / trả hàng: lỗi `INVALID_STATUS_TRANSITION` → "Đã có người xử lý" + tải lại (kiểm có sẵn chưa). `SupportChat` gọi `useAutoReadNotifications("SUPPORT_MESSAGE")` (cả tin tới trong lúc mở, không toast). Tải lại đã có sẵn ở `OrderDetailView` / `ReturnDetailView` — chỉ đổi câu báo thành "… đã có người xử lý. Đã tải lại …" (`message.warning`). `tsc`, eslint, `next build` sạch; **chưa thử tay** (NT-34) | NT-30 | — **điểm dừng 4** |

### Phần 9 — Hoàn tất
| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] NT-33 | QA | `NotificationRealtimeIntegrationTest`; `./scripts/agent-lint.sh`, `./scripts/agent-test.sh`. **4 test** (khách nhận `CREATED` đúng phong bì + giờ +07:00, người khác không nhận; dòng gộp → `UPDATED` cùng id, `eventCount` 2; NV A xử lý → B nhận `RESOLVED` kèm tên A + `unreadCount` 0, A không nhận; Hệ thống xử lý → cả hai nhận, `resolvedBy` null). Đột biến bỏ loại người xử lý → đỏ. Cả bộ **661 test xanh**; frontend `tsc` + eslint sạch, `next build` sạch (chạy ở Phần 8 — lần này không build vì `npm run dev` đang chạy) | tất cả | Xanh |
| - [ ] NT-34 | QA | Thử tay 3 cửa sổ (mục "Cách test" frontend). **2026-11-27 phần API + WS đã chạy trên stack local thật** (kịch bản Node, STOMP qua SockJS như trình duyệt; tài khoản seed khách + admin + staff): khách nhắn → 2 NV nhận `SUPPORT_NEW` đúng link; admin trả lời → staff nhận `RESOLVED` bởi "Admin Dev", admin không nhận lại, khách nhận `SUPPORT_MESSAGE`; lọc `resolved=false`; đọc 1 mục giảm badge; đọc của người khác 404; đọc rồi → tin sau tạo dòng mới; `read-all` → 0; không token 401 — 18/18 đạt. **Còn: thử giao diện trên trình duyệt** (badge, dropdown, toast, mục mờ, 390px, mất mạng) — người dùng | NT-32 | Đạt |
| - [ ] NT-35 | Docs | Trạng thái `pages.md`, tick `backend-roadmap.md` (F-09 phần notifications, N4-03, N4-04), `.agents/progress.md`, chuyển plan sang `completed/`, kiểm lại V17. **2026-11-27:** roadmap đã tick F-09 / N4-03 / N4-04; V17, V18 vẫn trống trên mọi nhánh `origin` (cao nhất V16 ở `dev`). **Còn:** đổi 🟡 → ✅ trong `pages.md` và chuyển `notification.md` + `favorite.md` sang `completed/` sau khi người dùng thử giao diện xong | NT-33 | |
