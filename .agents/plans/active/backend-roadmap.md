# Plan: Xây dựng backend Campvivo — phân công 4 người, chia task nhỏ

- **Ngày tạo:** 2026-10-17
- **Người phụ trách:** cả nhóm (bảng phân công `AGENTS.md` mục 9)
- **Tier:** 1 + 2 (Tier 3 — MoMo, chatbot AI — không nằm trong plan này)
- **Nguồn đặc tả:** `docs/02-ky-thuat/database.md` (bảng, BR, máy trạng thái, khoá), `api.md`
  (endpoint, mã lỗi), `features.md` (luồng + ngoại lệ), `authentication.md`, `architecture.md`.
  Plan này KHÔNG lặp lại đặc tả — mỗi task chỉ trỏ tới mục cần đọc.
- Thay thế phần "Thứ tự thực hiện" của `backend-schema-v1.md`: migration được tách theo người
  (task F-05 → F-09) để làm song song.

## 1. Cách dùng plan này

**Quy tắc chia task**
- Mỗi task ≈ 0,5–1,5 ngày công, làm trên 1 nhánh `feature/<module>-<task-id>` (vd.
  `feature/order-n1-04`), mở 1 PR vào `dev`. Task lớn hơn → tách tiếp, không gộp.
- Mỗi task có cột **Kiểm tra** = điều kiện "xong" cụ thể: test tự động phải có + cách thử tay.
- Làm theo thứ tự ID trong cột của mình; chỉ bắt đầu task khi các task ở cột **Phụ thuộc** đã merge
  (hoặc dùng stub ở F-11).

**Definition of Done (áp cho MỌI task)**
1. Code đúng `database.md` / `api.md` (tên field, mã lỗi, HTTP status).
2. Test: ≥ 1 test happy path + ≥ 1 test nhánh lỗi chính. Logic khoá/tiền/trạng thái → integration
   test Testcontainers (không mock DB).
3. Thêm request mẫu vào collection API dùng chung `tests/api/campvivo.postman_collection.json`
   (thư mục theo module, có cả request lỗi).
4. `./scripts/agent-lint.sh` và `./scripts/agent-test.sh` xanh; CI xanh.
5. Docs đổi nếu hành vi đổi (`documentation-sync`); 1 dòng vào `.agents/progress.md`.
6. Tick ô task trong plan này.

**Mẹo debug chung (thiết lập ở Giai đoạn 0)**
- Swagger UI (`/swagger-ui.html`) để gọi thử endpoint ngay sau khi viết.
- Mỗi request có `X-Request-Id` (filter ghi vào MDC) → lọc log theo một request.
- Thời gian lấy từ bean `java.time.Clock` (không gọi `now()` trực tiếp) → test OTP hết hạn, đơn quá
  15 phút, hạn trả hàng bằng cách đổi clock, không phải `sleep`.
- Bật `logging.level.org.hibernate.SQL=debug` ở profile dev khi cần xem câu SQL/khoá.
- Lỗi khó: tái hiện bằng test trước (skill `debugging`), rồi mới sửa.

## 2. Mốc tiến độ

| Mốc | Tuần | Nội dung | Kiểm tra "đạt mốc" (smoke test) |
|---|---|---|---|
| **M0 — Nền móng** | 3 | Giai đoạn 0: dependency, common, schema 22 bảng, security, interface giữa module, seed | App chạy với `ddl-auto=validate`; gọi endpoint bảo vệ không token → 401 đúng format; seed dev đăng nhập được bằng token test |
| **M1 — Mua hàng COD chạy được** | 4–5 | Auth đầy đủ, catalog public, cart, đặt hàng COD, nhân viên xử lý đơn | Đăng ký → OTP → đăng nhập → thêm giỏ → đặt COD → nhân viên CONFIRMED → SHIPPING → COMPLETED; tồn kho đúng BR-2 |
| **M2 — Tier 1 hoàn chỉnh** | 6 | VNPay + IPN, job timeout, admin sản phẩm/danh mục/tồn kho/user, test đồng thời | Thanh toán VNPay sandbox thành công/thất bại; IPN lặp xử lý 1 lần; 20 request song song không bán vượt |
| **M3 — Tier 2 nghiệp vụ** | 7–8 | Combo, voucher, chiến dịch, review, trả hàng, yêu thích, bài viết, tìm kiếm | Giá chiến dịch hiển thị và đặt hàng khớp; voucher hết lượt chặn đúng; trả hàng cộng kho, đơn RETURNED |
| **M4 — Realtime & hệ thống** | 8–9 | WebSocket, thông báo, email, chat, gợi ý, dashboard | Đổi trạng thái đơn → khách nhận thông báo realtime + email; chat 2 chiều; dashboard đúng số liệu seed |
| **M5 — Ổn định** | 10 | Test phân quyền toàn bộ, E2E nhiều module, sửa lỗi, đóng băng API | Bộ E2E xanh; không còn endpoint nào trong `api.md` chưa làm; frontend nối API xong các luồng chính |

Tuần 11–12: frontend hoàn thiện, deploy demo, viết báo cáo — backend chỉ sửa lỗi.

## 3. Sơ đồ phụ thuộc giữa module

```
F-01..F-04 (common, config, test base) ─┐
F-05..F-09 (migration + entity)         ├─► F-10 (security) ─► F-11 (interface + stub) ─► mọi task module
                                        ┘
N2 InventoryService (N2-03) ──► N1 đặt hàng (N1-04, N1-05), trả hàng (N1-12)
N2 PricingService (N2-13)   ──► N1 giỏ/đặt hàng dùng giá chiến dịch (N1-01, N1-03)
N2 VoucherService (N2-16)   ──► N1 preview/đặt hàng có voucher
N1 OrderQueryService (N1-07)──► N4 review "đã mua" (N4-01), N3 chi tiết user (N3-07), N2 analytics (N2-12)
N1 events (N1-06)           ──► N4 thông báo/email (N4-04, N4-05)
N3 WebSocket (N3-08)        ──► N4 thông báo realtime, chat (N4-04, N4-06)
N2 catalog query (N2-02)    ──► N4 favorites/recommendation trả ItemSummary (N4-02, N4-07)
```
Nhờ F-11 (interface + stub trả dữ liệu đơn giản), không ai phải ngồi chờ: N1 code đặt hàng gọi
`InventoryService` stub, N2 thay bằng bản thật sau mà N1 không sửa code.

## 4. Giai đoạn 0 — Nền móng (Tuần 3, mọi người)

Làm theo thứ tự: F-01 → F-04 song song với F-05 → F-09; rồi F-10, F-11, F-12, F-14.

| ID | Người | Task | Phụ thuộc | Kiểm tra |
|---|---|---|---|---|
| - [x] F-01 | N3 | Bổ sung dependency `pom.xml`: `jjwt` (api/impl/jackson), `spring-boot-starter-websocket`, `spring-boot-starter-thymeleaf`, `owasp-java-html-sanitizer`, `cloudinary-http44`, `springdoc-openapi-starter-webmvc-ui`, `greenmail-junit5` (test) | — | `mvn -q compile` + app khởi động; mở được `/swagger-ui.html` |
| - [ ] F-02 | N3 | `common`: enum `ErrorCode` (toàn bộ `api.md` mục 2 + HttpStatus + message VI), `BusinessException(ErrorCode, details)`, `GlobalExceptionHandler` (BusinessException, validation → `fieldErrors`, 403, 404, 500), `ApiResponse`, `PageResponse` (data + pagination), `Money` (HALF_UP), `SlugUtil` (bỏ dấu tiếng Việt), `HtmlSanitizer`, bean `Clock`, filter `X-Request-Id` | F-01 | Unit test `SlugUtil` ("Lều cắm trại 2 người" → `leu-cam-trai-2-nguoi`), `Money`; MockMvc test: controller giả ném từng loại lỗi → JSON đúng format `api.md` mục 1 |
| - [x] F-03 | N3 | `AppProperties` (`@ConfigurationProperties("app")`, đủ key bảng `tech-stack.md`), `application.yml` (`ddl-auto=validate`, `open-in-view=false`), `application-dev.yml.example`, cập nhật `.env.example` (thêm `NEXT_PUBLIC_WS_URL`) | F-01 | Test bind properties: đọc đúng giá trị mặc định (`app.order.payment-timeout = 15m`…) |
| - [ ] F-04 | N4 | Hạ tầng test: `AbstractIntegrationTest` (Testcontainers PostgreSQL dùng chung 1 container, MockMvc, dọn dữ liệu bằng `TRUNCATE ... RESTART IDENTITY CASCADE` sau mỗi test), `TestDataFactory` (tạo user/product/... tối giản), `TestAuth.tokenFor(user)`, `MutableClock` cho test; xoá/viết lại `OrderServiceIT` cũ | F-01 | 1 IT mẫu chạy xanh trên máy và CI; 2 test liên tiếp không thấy dữ liệu của nhau |
| - [ ] F-05 | N3 | Migration `V1__users.sql` (bảng `users`) + entity `User`, enum `Role`, `UserRepository`. Xoá `V1__init_tier1.sql`, `V2__tier2.sql` placeholder | F-03 | App khởi động `validate` OK; IT: chèn email trùng → lỗi unique |
| - [ ] F-06 | N2 | `V2__catalog.sql`: `categories`, `products`, `product_combos`, `images`, `combo_items`, `articles`, `inventory_logs` + entity/repository; sửa `Product`, `Category` hiện có cho khớp | F-05 | `validate` OK; IT: `reserved > stock` bị CHECK chặn; ảnh có cả product_id và combo_id bị chặn; 2 ảnh primary cho 1 sản phẩm bị chặn |
| - [ ] F-07 | N2 | `V3__promotion.sql`: `vouchers`, `discount_campaigns`, `discount_campaign_products` + entity | F-06 | `validate` OK; IT: `used_count > quantity` bị chặn; `expiry_date ≤ start_date` bị chặn |
| - [ ] F-08 | N1 | `V4__order.sql`: `carts`, `cart_items`, `orders`, `order_items`, `order_status_history`, `payments`, `return_requests` + entity; `OrderStatus` thêm `RETURNED` + `canTransitionTo` (bảng mục 5.1); bỏ `CartItem.priceAtAdd`; enum `PaymentMethod`, `PaymentStatus`, `ReturnStatus` | F-07 | `validate` OK; unit test `canTransitionTo` cho đủ mọi cặp trạng thái (hợp lệ/không hợp lệ); IT: cart_item trùng sản phẩm bị unique chặn |
| - [ ] F-09 | N4 | `V5__interaction.sql`: `reviews`, `favorites`, `support_messages`, `notifications` + entity; index mục 3.23 còn lại | F-08 | `validate` OK; IT: review rating 6 bị chặn; favorite trỏ 2 đối tượng bị chặn |
| - [ ] F-10 | N3 | Security khung: `JwtService` (tạo/đọc token, claim `tv`), `JwtAuthenticationFilter` (kiểm `token_version`, `is_active`, role từ DB), `SecurityFilterChain` theo path (`authentication.md` mục 5), entry point 401 / access-denied 403 trả JSON chuẩn, CORS, `@CurrentUserId` (resolver lấy userId) | F-02, F-05 | IT: không token → 401 `UNAUTHORIZED`; token CUSTOMER gọi `/api/admin/**` → 403 `FORBIDDEN`; token có `tv` cũ → 401; user bị khoá → 401 |
| - [ ] F-11 | Cả nhóm (N4 điều phối) | Interface + stub giữa module (`architecture.md`): `InventoryService` (reserve/release/commit/restock/lock), `PricingService`, `VoucherService` (validate/consume/release), `CartService.createForUser`, `OrderQueryService` (hasCompletedPurchase, statsForUser), `CatalogQueryService` (itemSummaries), `RealtimeSender`, `PaymentGateway`; các class sự kiện (`OrderCreatedEvent`, `OrderStatusChangedEvent`, `ReturnRequestChangedEvent`, `SupportMessageCreatedEvent`) | F-05 → F-09 | Compile xanh; mỗi interface có Javadoc ghi rõ hành vi + mã lỗi; stub có test nhỏ; cả 4 người review PR này |
| - [ ] F-12 | N3 (mỗi người gửi dữ liệu module mình) | Seed dev `V6__seed_dev.sql` chạy riêng profile dev (`spring.flyway.locations`): 3 tài khoản (`local-development.md`), cây danh mục, ~20 sản phẩm, 3 combo, 2 voucher, 1 chiến dịch, vài bài viết — không dùng nội dung cửa hàng tham khảo | F-05 → F-09 | `docker compose up` + chạy dev → đăng nhập 3 tài khoản được (sau N3-03); profile test KHÔNG có seed |
| - [ ] F-13 | N4 | Tạo `tests/api/campvivo.postman_collection.json` + environment (baseUrl, token theo role), script lưu token sau login; hướng dẫn trong `tests/README.md` | F-10 | Chạy collection rỗng/khung không lỗi; mọi người import được |
| - [ ] F-14 | N3 | Nền FE gọi API (cả nhóm dùng): `services/apiClient.ts` (axios, gắn token, bóc `data`, lỗi → `ApiError {errorCode, message, details}`, 401 → xoá token), helper gọi API phía server cho SSR, `lib/errorMessages.ts` (map `errorCode` → tiếng Việt), `types/api/common.ts` (`Money`, `PageResponse`, enum) | F-10 | `npm run build` + `lint` xanh; trang thử gọi `/actuator/health` qua client hiển thị kết quả; lỗi 4xx hiển thị đúng message tiếng Việt |

## 5. Người 1 — Mua hàng: `cart`, `order` (gồm trả hàng), `payment`

| ID | Tuần | Task | Phụ thuộc | Kiểm tra |
|---|---|---|---|---|
| - [ ] N1-01 | 4 | `CartService.createForUser` (gọi từ đăng ký) + `GET /cart`: dòng giỏ với giá hiện hành (qua `PricingService`), `availableQuantity`, `isActive`, `lineTotal`, `subtotal`, `hasProblems` | F-11 | IT: giỏ rỗng; giỏ có sản phẩm đã ẩn → `hasProblems=true`; đổi giá sản phẩm → `GET /cart` thấy giá mới (D-04) |
| - [ ] N1-02 | 4 | `POST /cart/items` (productId hoặc comboId, cộng dồn), `PATCH`, `DELETE /cart/items/{id}`; lỗi `OUT_OF_STOCK` (tính cả số đã trong giỏ), `PRODUCT_INACTIVE`, sửa dòng của người khác → 404 | N1-01 | IT từng lỗi; gửi cả 2 id hoặc không id nào → 400 `VALIDATION_ERROR` |
| - [ ] N1-03 | 4 | `OrderPricingCalculator` (thuần Java, không DB): subtotal, voucher, phí ship (D-17), final. `POST /orders/preview`, `POST /vouchers/validate` (giỏ hiện tại) | N1-01 | Unit test bảng: không voucher / PERCENTAGE có trần / FIXED > subtotal / ngưỡng freeship đúng biên 1.000.000 |
| - [ ] N1-04 | 5 | `POST /orders` luồng **COD**: đúng trình tự `database.md` mục 6 (khoá theo thứ tự, BR-1/2/4/5/14, `PRICE_CHANGED`, snapshot `order_items`, payment PENDING, history, xoá dòng giỏ đã đặt, `saveAsDefault`), phát `OrderCreatedEvent` | N1-02, N1-03, F-11 | IT: đặt thành công → `reserved` tăng, giỏ rỗng; `expectedTotal` sai → 409, DB không đổi gì; hết hàng → 409 kèm `details.items`; giỏ rỗng → `CART_EMPTY` |
| - [ ] N1-05 | 5 | Test đồng thời đặt hàng (dùng `InventoryService` thật khi N2-03 xong): 20 luồng / sản phẩm còn 5; tương tự combo; 2 giỏ chéo nhau (A,B) và (B,A) không deadlock; voucher còn 1 lượt | N1-04, N2-03, N2-16 | Đúng 5 đơn thành công, `reserved=5`; không có `deadlock detected`; đúng 1 đơn dùng được voucher |
| - [ ] N1-06 | 5 | `OrderStateService.changeStatus(orderId, to, actorId, note)`: khoá đơn, kiểm `canTransitionTo`, hiệu ứng theo bảng BR-2 (commit/release kho, trả lượt voucher, `completed_at`, COD → payment SUCCESS), ghi history, phát `OrderStatusChangedEvent` | N1-04 | IT cho từng dòng bảng 5.1 (kiểm tồn kho + voucher sau mỗi chuyển); chuyển sai → `INVALID_STATUS_TRANSITION` |
| - [ ] N1-07 | 5 | Phía khách: `GET /orders` (lọc, phân trang), `GET /orders/{id}` (đủ field `OrderDetail`, `canCancel`, `canReturn`, `canReview`), `POST /orders/{id}/cancel`; `OrderQueryService` thật (hasCompletedPurchase, statsForUser) | N1-06 | IT: xem đơn người khác → 404; huỷ khi CONFIRMED → `ORDER_NOT_CANCELLABLE`; huỷ PENDING trả kho + lượt voucher |
| - [ ] N1-08 | 5 | Phía nhân viên: `GET /admin/orders` (lọc trạng thái/phương thức/ngày, tìm mã đơn/email/SĐT), `GET /admin/orders/{id}`, `PATCH /admin/orders/{id}/status` (chỉ đơn COD được xác nhận/huỷ tay; huỷ bắt buộc note) | N1-06 | IT: EMPLOYEE xác nhận COD OK; xác nhận đơn VNPay PENDING → `INVALID_STATUS_TRANSITION`; CUSTOMER gọi → 403 |
| - [ ] N1-09 | 6 | `VnpayGateway.createPaymentUrl` (sắp xếp tham số, HMAC-SHA512, `vnp_ExpireDate` = timeout, IP khách) + `verifyIpn`; nối vào `POST /orders` khi `VNPAY` (lỗi → `PAYMENT_GATEWAY_ERROR`, rollback) | N1-04 | Unit test chữ ký với bộ tham số mẫu trong tài liệu VNPay; URL mở được trên sandbox |
| - [ ] N1-10 | 6 | `GET /payments/vnpay/ipn` + `PaymentService.handleIpnResult` (BR-12: chữ ký, số tiền, idempotent, đơn đã huỷ → log WARN, trả `RspCode` đúng chuẩn); `GET /payments/vnpay/return` chỉ đọc | N1-09, N1-06 | IT: IPN thành công → CONFIRMED, kho trừ; gọi lại lần 2 → không đổi gì; sai chữ ký → `97`; sai số tiền → `04`; thất bại → PAYMENT_FAILED, trả kho + voucher |
| - [ ] N1-11 | 6 | `OrderTimeoutJob` (BR-3): mỗi đơn 1 transaction, khoá + kiểm lại trạng thái | N1-06 | IT với `MutableClock`: đơn VNPay 16 phút → CANCELLED; 14 phút → giữ; đơn COD 2 ngày → giữ; IPN và job chạy cùng lúc không làm hỏng dữ liệu |
| - [ ] N1-12 | 7 | Trả hàng: `POST /orders/{id}/returns` (BR-9, dùng `completed_at` + clock); `GET /admin/returns`, `GET /admin/returns/{id}`, `PATCH /admin/returns/{id}` (APPROVE/REJECT/COMPLETE; COMPLETE → `InventoryService.restock` + log RETURN + đơn RETURNED); phát `ReturnRequestChangedEvent` | N1-06, N2-03 | IT: quá 7 ngày → `RETURN_WINDOW_EXPIRED`; gửi 2 lần → `RETURN_ALREADY_EXISTS`; REJECT thiếu note → 400; COMPLETE cộng đúng kho |
| - [ ] N1-13 | 7 | Thử tay VNPay sandbox end-to-end qua ngrok (theo `local-development.md`): thành công, huỷ trên cổng, bỏ dở để hết hạn; ghi kết quả + lỗi gặp vào `troubleshooting.md` | N1-10, N1-11 | Checklist 3 kịch bản đạt; có ảnh chụp để đưa vào báo cáo |

## 6. Người 2 — Quản trị (full-stack): `catalog`, `combo`, `inventory`, `promotion`, `article`, `analytics` + trang storefront tương ứng + giao diện admin

**Cách làm: lát cắt dọc.** Mỗi chức năng = 1 task **BE** rồi ngay sau là 1 task **FE** dùng chính API
đó. Lỗi ở task FE mà API đã có test xanh → lỗi nằm ở FE (hoặc adapter); ngược lại thì kiểm lại BE
bằng Swagger/Postman trước khi sửa FE.

**Thứ tự ưu tiên**
1. **Giai đoạn A (tuần 4–5) — API đọc + nối storefront đã có:** dữ liệu lấy từ seed (F-12), không cần
   form admin. Xen vào `InventoryService` sớm vì Người 1 cần cho đặt hàng.
2. **Giai đoạn B (tuần 6) — giá & khuyến mãi:** `PricingService`, chiến dịch, voucher (Người 1 cần cho
   checkout).
3. **Giai đoạn C (tuần 6–9) — admin:** API ghi + giao diện admin dựng trên API đã chạy, không làm UI mock.
4. **Giai đoạn D (tuần 9) — dọn mock.**

**Quy ước FE cho phần này**
- Gọi API qua `services/catalogService.ts` (viết lại dần từng hàm; giữ tên hàm hiện có như
  `getProduct`, `getCategories`… để ít phải sửa component). Trang public render phía server (SSR).
- Response API (`ItemSummary`, `ProductDetail`…) khác kiểu mock (`ProductCard`…) → viết **adapter** trong
  service (`toProductCard(item)`) để component cũ chạy được; field mock không có trong API (`sold`,
  `memberPrice`, `viewsToday`, `stockUrgency`, `swatches`…) thì ẩn phần UI đó, không bịa dữ liệu.
- Cờ `NEXT_PUBLIC_CATALOG_SOURCE=mock|api` trong `.env.local`: chuyển qua lại mock/API khi debug hoặc
  khi BE chưa chạy. Xoá cờ ở N2-33.
- Mỗi trang nối API phải có loading / empty / error (`ui-guidelines.md`).

### Giai đoạn A — API đọc + storefront (tuần 4–5)

| ID | Loại | Tuần | Task | Phụ thuộc | Kiểm tra |
|---|---|---|---|---|---|
| - [ ] N2-01 | BE | 4 | `GET /categories` (cây nhiều cấp từ `path`) + `CategoryService` dùng chung (dựng cây, lấy id cây con) | F-11, F-12 | IT: seed 2 cấp → cây đúng thứ tự; danh mục không có con trả `children: []` |
| - [ ] N2-02 | BE | 4 | `GET /products` (category gồm danh mục con, brand, sort `newest/price_asc/price_desc` theo giá gốc, phân trang), `GET /products/{slug}` (`ProductDetail`: ảnh, breadcrumb danh mục), `GET /brands`; `CatalogQueryService.itemSummaries` thật | N2-01 | IT: lọc danh mục cha có sản phẩm của danh mục con; sản phẩm ẩn không có trong list, chi tiết → 404; `availableQuantity = stock − reserved` |
| - [ ] N2-03 | BE | 4 | `InventoryService` thật: `lockItems` theo thứ tự product → combo, `reserve/release/commit/restock` cho cả product và combo (BR-2, `database.md` mục 6) — **Người 1 chờ task này** | F-11 | IT: reserve vượt available → `OUT_OF_STOCK`; commit trừ cả stock & reserved; 2 luồng reserve cùng lúc không vượt |
| - [ ] N2-04 | FE | 4 | Nền FE catalog: kiểu `types/api/catalog.ts` theo `api.md` mục 3–4.3, viết lại `catalogService` gọi API qua `apiClient` (F-14) + adapter + cờ `NEXT_PUBLIC_CATALOG_SOURCE` | F-14, N2-02 | `npm run build` xanh ở cả 2 chế độ mock/api; tắt BE → trang báo lỗi thân thiện, không trắng trang |
| - [ ] N2-05 | FE | 5 | Nối danh sách: menu header từ `GET /categories` (giữ mục đặc biệt tĩnh), `/products`, `/categories/[slug]`, `/categories/san-pham-moi` (`sortBy=newest`), `/brands`, `/brands/[slug]` — bộ lọc/sort/phân trang đẩy lên query string | N2-04 | Thử tay từng route với dữ liệu seed; đổi trang/sort → URL đổi, F5 giữ trạng thái; danh mục rỗng → empty state |
| - [ ] N2-06 | FE | 5 | Nối `/products/[slug]`: gallery, giá, tồn kho khả dụng (hết hàng → nút disable), breadcrumb; ẩn khối không có dữ liệu thật; nút "Thêm vào giỏ" giữ mock đến khi Người 1 xong giỏ | N2-04 | Slug sai → trang 404; sản phẩm hết hàng hiện "Hết hàng"; không còn đọc `pdp-static`/`cards.json` cho phần đã có API |
| - [ ] N2-07 | BE | 5 | `GET /combos`, `GET /combos/{slug}` (thành phần kèm `ItemSummary` sản phẩm, tồn kho riêng D-03) | N2-02 | IT: combo ẩn → 404; `items` trả đúng số lượng từng thành phần |
| - [ ] N2-08 | FE | 5 | Trang mới `/combos`, `/combos/[slug]` (tái dùng `ListingView`, `ProductPage`); khối combo ở trang chủ | N2-07, N2-05 | Thử tay; combo hết hàng hiển thị đúng; thêm link "Combo" vào menu |
| - [ ] N2-09 | BE | 5 | `GET /articles?category=`, `GET /articles/{slug}` (chỉ bài đã xuất bản) | F-11 | IT: bài nháp không thấy; lọc `JOURNAL` chỉ ra bài journal; sắp mới nhất trước |
| - [ ] N2-10 | FE | 5 | Nối `/blog`, `/blog/page/[n]`, `/blog/[slug]`, `/journal*`, `/guides` theo `category`; gỡ `/blog/category/*`, `/journal/category/*` (KI-05) | N2-09, N2-04 | Thử tay phân trang; slug sai → 404; route danh mục con đã bỏ không còn link trỏ tới |
| - [ ] N2-11 | BE | 5 | `GET /search` (product/combo/article, không phân biệt hoa thường, có/không `type`) | N2-07, N2-09 | IT: "LỀU" ra "Lều…"; `q` 1 ký tự → 400; không `type` → mỗi nhóm tối đa `limit` |
| - [ ] N2-12 | FE | 5 | Nối `/search` (thêm tab Combo) + ô tìm kiếm header; nối các khối dữ liệu trang chủ (sản phẩm mới, combo, bài viết — banner/hero giữ tĩnh trong FE vì không có bảng banner) | N2-11, N2-08 | Thử tay; từ khoá không có kết quả → empty state; trang chủ không còn đọc `cards.json` |

### Giai đoạn B — Giá & khuyến mãi (tuần 6)

| ID | Loại | Tuần | Task | Phụ thuộc | Kiểm tra |
|---|---|---|---|---|---|
| - [ ] N2-13 | BE | 6 | `PricingService` thật (BR-6: mức giảm lớn nhất, không cộng dồn, không áp combo) dùng cho mọi `ItemSummary`; `GET /campaigns/{slug}` — **Người 1 dùng cho giỏ/đặt hàng** | N2-02 | Unit test bảng: không chiến dịch / 2 chiến dịch chồng / hết hạn / FIXED > giá → 0; IT: `GET /products` có `originalPrice` + `price` |
| - [ ] N2-14 | BE | 6 | `GET /products` lọc `minPrice/maxPrice` và sort theo **giá sau chiến dịch** (cột giá tính sẵn bằng subquery/CTE) | N2-13 | IT: 500k giảm 20% lọt khoảng 300–450k; `price_asc` đúng thứ tự giá sau giảm |
| - [ ] N2-15 | FE | 6 | Hiển thị giá thống nhất (component `PriceTag`: giá sau giảm + giá gốc gạch ngang + % giảm) ở thẻ, chi tiết, combo; nối `/promotions/[slug]` | N2-13, N2-05 | Thử tay sản phẩm có/không chiến dịch; chiến dịch hết hạn → `/promotions/[slug]` 404 |
| - [ ] N2-16 | BE | 6 | `VoucherService` thật: `calculate`, `consume` (khoá dòng, kiểm lại BR-5, `used_count + 1`), `release`; API admin `GET/POST/PUT/DELETE /admin/vouchers` (`VOUCHER_IN_USE`) — **Người 1 dùng cho checkout** | F-11 | Unit test điều kiện + thứ tự mã lỗi BR-5; IT: hết lượt → `VOUCHER_EXHAUSTED`; dùng lần 2 → `VOUCHER_ALREADY_USED`; xoá voucher đã dùng → 409 |

### Giai đoạn C — Admin: API ghi + giao diện (tuần 6–9)

| ID | Loại | Tuần | Task | Phụ thuộc | Kiểm tra |
|---|---|---|---|---|---|
| - [ ] N2-17 | FE | 6 | *(Phần khung đã làm sớm 2026-10-17: root layout `(admin)`, antd, `AdminShell`, menu theo role, `RequireRole`, phiên giả lập — còn lại: nối auth thật khi có F-14/N3-03 và các component `components/admin/common`)* **Khung admin** (cả nhóm dùng lại): `app/admin/layout.tsx` (chặn chưa đăng nhập → `/login?redirect=`, sai role → trang 403, Employee vào `/admin` → `/admin/orders`), sidebar theo role (`pages.md` mục Admin); component `components/ui`: `DataTable` (cột, sort, phân trang), `FormField`, `ConfirmDialog`, `ImageUploader` (gọi N3-09), `Badge` trạng thái — thống nhất với Người 3 (chủ `components/ui`) | F-14, N3-03 | Thử tay với 3 tài khoản seed: CUSTOMER bị chặn, EMPLOYEE chỉ thấy 4 menu, ADMIN thấy đủ; trang mẫu dùng `DataTable` với dữ liệu giả |
| - [ ] N2-18 | BE | 7 | `GET/POST/PUT/DELETE /admin/categories` (BR-13: `path`, đổi cha cập nhật cây con, `CIRCULAR_CATEGORY_DEPENDENCY`, `CATEGORY_NOT_EMPTY`, slug trùng) | N2-01 | IT: chuyển nút có 2 cấp con sang cha khác → `path` cả cây đúng; chọn cha là cháu của mình → 400 |
| - [ ] N2-19 | FE | 7 | `/admin/categories`: cây danh mục, modal thêm/sửa (chọn cha), xoá có xác nhận, hiển thị lỗi theo `errorCode` | N2-17, N2-18 | Thử tay đủ 3 lỗi (vòng lặp, còn con, slug trùng) → thông báo tiếng Việt đúng |
| - [ ] N2-20 | BE | 7 | `GET /admin/products` (q, categoryId, isActive), `GET/POST/PUT /admin/products/{id}` (slug tự sinh + chống trùng, sanitize mô tả, đúng 1 ảnh chính) | N2-02, N3-09 | IT: không slug → sinh từ tên; `<script>` bị loại; 2 ảnh chính → 400 |
| - [ ] N2-21 | FE | 7 | `/admin/products`, `/admin/products/new`, `/admin/products/[id]`: bảng lọc/tìm, form (upload nhiều ảnh, chọn ảnh chính, mô tả HTML bằng textarea hoặc editor nhẹ), bật/tắt kinh doanh | N2-17, N2-20 | Tạo sản phẩm mới → thấy ngay ở storefront; tắt `isActive` → biến mất khỏi storefront |
| - [ ] N2-22 | BE | 8 | `GET/POST/PUT /admin/combos` (≥ 2 thành phần, ảnh, danh mục) | N2-07, N2-20 | IT: 1 thành phần → 400; sản phẩm thành phần không tồn tại → 404 |
| - [ ] N2-23 | FE | 8 | `/admin/combos` (+ new/[id]): chọn sản phẩm thành phần + số lượng, giá combo, ảnh | N2-21, N2-22 | Thử tay tạo combo → hiện ở `/combos` |
| - [ ] N2-24 | BE | 8 | `GET /admin/inventory` (type, q, lowStock), `POST /admin/inventory/adjustments` (BR-8, `INVALID_STOCK_ADJUSTMENT`), `GET /admin/inventory/logs` | N2-03 | IT: xuất làm `stock < reserved` → 400; mỗi điều chỉnh có đúng 1 log, `stock_after` đúng; EMPLOYEE gọi được |
| - [ ] N2-25 | FE | 8 | `/admin/inventory`: tab Sản phẩm / Combo, lọc "sắp hết hàng", modal điều chỉnh, tab nhật ký | N2-17, N2-24 | Nhập kho → `available` tăng ở storefront; EMPLOYEE thao tác được |
| - [ ] N2-26 | FE | 8 | `/admin/vouchers`: bảng, modal thêm/sửa, tắt/xoá (lỗi `VOUCHER_IN_USE` → gợi ý tắt) | N2-17, N2-16 | Thử tay; voucher mới dùng được ở checkout (khi Người 1 xong) |
| - [ ] N2-27 | BE | 8 | `GET/POST/PUT/DELETE /admin/campaigns` (gán danh sách sản phẩm) | N2-13 | IT: `endDate ≤ startDate` → 400; gán sản phẩm → giá hiển thị đổi ngay |
| - [ ] N2-28 | FE | 9 | `/admin/campaigns` (+ new/[id]): form + chọn sản phẩm áp dụng (tìm kiếm, chọn nhiều) | N2-17, N2-27 | Tạo chiến dịch → `/promotions/[slug]` và giá thẻ sản phẩm đổi |
| - [ ] N2-29 | BE | 9 | `GET/POST/PUT/DELETE /admin/articles` (sanitize, `published_at` gán lần đầu xuất bản) | N2-09 | IT: gỡ xuất bản rồi xuất bản lại giữ `publishedAt` cũ |
| - [ ] N2-30 | FE | 9 | `/admin/articles` (+ new/[id]): soạn bài (editor nhẹ), phân loại BLOG/JOURNAL/GUIDE, lưu nháp/xuất bản | N2-17, N2-29 | Bài xuất bản hiện ở `/blog` hoặc `/journal` đúng loại |
| - [ ] N2-31 | BE | 9 | `/admin/analytics/summary`, `/revenue?groupBy=day\|month\|year` (giờ `Asia/Ho_Chi_Minh`, theo `completed_at`, không tính RETURNED), `/top-items` | N1-07 | IT: đơn `completed_at` 23:30 ngày 31/12 (UTC+7) rơi đúng ngày/tháng/năm; RETURNED không tính |
| - [ ] N2-32 | FE | 9 | Dashboard `/admin`: thẻ số liệu, biểu đồ doanh thu (Recharts) chọn khoảng ngày + gom ngày/tháng/năm, bảng top mặt hàng | N2-17, N2-31 | Số liệu khớp truy vấn SQL tay trên dữ liệu seed |

### Giai đoạn D — Dọn dẹp (tuần 9)

| ID | Loại | Tuần | Task | Phụ thuộc | Kiểm tra |
|---|---|---|---|---|---|
| - [ ] N2-33 | FE | 9 | Xoá mock phần catalog: `cards.json`, `categories.json`, `brands.json`, `products/`, `articles/`, `journal/`, `pdp-*.json`, `services/mockContent.ts`, route `app/api/mock/*` không còn dùng, cờ `NEXT_PUBLIC_CATALOG_SOURCE`; cập nhật cột "Trạng thái" trong `pages.md` | toàn bộ Giai đoạn A–C | `grep` không còn import từ `mocks/` cho phần catalog; `npm run build` xanh; thử lại toàn bộ route storefront |

## 7. Người 3 — Xác thực & người dùng: `auth`, `user`, `common` (+ hạ tầng realtime, upload)

| ID | Tuần | Task | Phụ thuộc | Kiểm tra |
|---|---|---|---|---|
| - [ ] N3-01 | 4 | `OtpService` (BR-11: sinh `SecureRandom`, hash BCrypt, TTL, đếm sai, chống gửi lại sớm) + `MailService` gửi đồng bộ, template Thymeleaf `otp.html` | F-10 | Unit test với `MutableClock`: hết hạn, sai 5 lần, gửi lại < 60s; GreenMail nhận được thư chứa mã |
| - [ ] N3-02 | 4 | `POST /auth/register` (email chưa xác thực → ghi đè + gửi lại mã), `POST /auth/verify-email` (trả token), `POST /auth/resend-code`; gọi `CartService.createForUser` | N3-01 | IT: đăng ký → user `email_verified=false` + có giỏ; verify đúng mã → token dùng được; email đã xác thực → `EMAIL_ALREADY_EXISTS`; lỗi gửi mail → `EMAIL_SEND_FAILED`, không tạo user |
| - [ ] N3-03 | 4 | `POST /auth/login` (`INVALID_CREDENTIALS`, `EMAIL_NOT_VERIFIED`, `ACCOUNT_DISABLED`), `POST /auth/logout` (tăng `token_version`) | N3-02 | IT: logout xong token cũ gọi `/users/me` → 401; sai mật khẩu và email không tồn tại trả cùng 1 lỗi |
| - [ ] N3-04 | 4 | `POST /auth/forgot-password` (luôn 200), `POST /auth/reset-password` (đổi hash, tăng `token_version`, set `email_verified`) | N3-01 | IT: email không tồn tại → 200, không gửi thư; reset xong token cũ → 401, mật khẩu mới đăng nhập được |
| - [ ] N3-05 | 5 | `GET/PUT /users/me`, `PUT /users/me/password` (`INVALID_CURRENT_PASSWORD`, tăng `token_version`) | N3-03 | IT: không sửa được email; đổi mật khẩu sai mật khẩu cũ → 400 |
| - [ ] N3-06 | 5 | `GET /admin/users` (q theo email/họ tên, role, isActive, phân trang) | N3-03 | IT: chỉ ADMIN; EMPLOYEE → 403; tìm "nguyen" không phân biệt hoa thường |
| - [ ] N3-07 | 5 | `GET /admin/users/{id}` (+ số đơn, tổng chi tiêu qua `OrderQueryService`), `PATCH /admin/users/{id}` (đổi role/khoá → tăng `token_version`; `CANNOT_MODIFY_SELF`) | N3-06, N1-07 | IT: khoá user → token của họ bị 401 ngay; admin tự hạ quyền → 400 |
| - [ ] N3-08 | 6 | WebSocket: STOMP `/ws`, `ChannelInterceptor` xác thực JWT khi CONNECT, principal = userId, `RealtimeSender.toUser(id, dest, payload)` / `toStaff(dest, payload)` | F-10 | IT bằng `WebSocketStompClient`: kết nối có token nhận được tin gửi tới mình; không token → bị từ chối; user A không nhận tin của B |
| - [ ] N3-09 | 6 | `POST /admin/uploads/images` (Cloudinary; chỉ jpg/png/webp ≤ 5MB) | F-10 | Test với client Cloudinary giả: file 6MB → 400; file .gif → 400; thành công trả `url` |
| - [ ] N3-10 | 9 | Test ma trận phân quyền: test tham số hoá gọi mọi nhóm endpoint trong `api.md` với Guest / CUSTOMER / EMPLOYEE / ADMIN → đúng 200/401/403 theo `authentication.md` mục 2 | gần hết task module | Bảng kết quả khớp ma trận; endpoint mới phải thêm vào test này |

## 8. Người 4 — Khám phá & hỗ trợ + Hệ thống: `review`, `favorite`, `support`, `notification`, `recommendation` (+ QA)

| ID | Tuần | Task | Phụ thuộc | Kiểm tra |
|---|---|---|---|---|
| - [ ] N4-01 | 5 | Review: `POST /reviews` (BR-10 qua `OrderQueryService.hasCompletedPurchase`), `PUT/DELETE /reviews/{id}` (chủ sở hữu), `GET /products/{id}/reviews`, `GET /combos/{id}/reviews` (điểm TB + số lượt), `GET /users/me/reviews`; `ReviewSummaryService` cho catalog | F-11 (stub), N1-07 (thật) | IT: chưa mua → `NOT_PURCHASED`; viết lần 2 → `REVIEW_ALREADY_EXISTS`; xoá rồi viết lại OK; đơn RETURNED không tính đã mua |
| - [ ] N4-02 | 5 | Favorite: `POST /favorites` (idempotent), `DELETE /favorites/{id}`, `DELETE /favorites?productId=…`, `GET /users/me/favorites?type=`; cờ `isFavorite` cho trang chi tiết | N2-02 | IT: thêm 2 lần → cùng id; danh sách trả đúng `ItemSummary`/`ArticleSummary`; xoá favorite người khác → 404 |
| - [ ] N4-03 | 6 | Notification lõi: `NotificationService.create`, `GET /notifications?isRead=` (+ `unreadCount`), `PATCH /notifications/{id}/read`, `PATCH /notifications/read-all` | F-09 | IT: read-all chỉ đánh dấu của chính mình; `unreadCount` đúng sau khi đọc 1 cái |
| - [ ] N4-04 | 7 | Listener sự kiện (`@TransactionalEventListener(AFTER_COMMIT)`): đơn mới (→ mọi Staff), đơn đổi trạng thái (→ khách), trả hàng (→ khách / Staff), tạo notification (`REQUIRES_NEW`) + đẩy WebSocket qua `RealtimeSender` | N4-03, N3-08, N1-06 | IT: đổi trạng thái → có notification + client STOMP nhận được; transaction rollback → không có notification |
| - [ ] N4-05 | 7 | Email nghiệp vụ `@Async`: đặt hàng thành công, đổi trạng thái đơn, kết quả trả hàng (template Thymeleaf, link `app.frontend-url`); lỗi gửi chỉ log | N4-04 | GreenMail: nhận đúng thư theo từng sự kiện; SMTP lỗi → nghiệp vụ vẫn thành công |
| - [ ] N4-06 | 8 | Chat hỗ trợ: `GET/POST /support/messages` (khách), `GET /admin/support/conversations`, `GET/POST /admin/support/conversations/{customerId}/messages` (`is_from_staff=true`, D-05), đẩy WS cả 2 phía, notification khi nhân viên trả lời | N3-08, N4-03 | IT: khách không đọc được hội thoại người khác; danh sách hội thoại sắp theo tin mới nhất; STOMP: tin nhân viên tới đúng khách |
| - [ ] N4-07 | 8 | Gợi ý mua kèm (FR-25): `GET /products/{id}/recommendations`, `GET /combos/{id}/recommendations` — SQL co-purchase trên đơn COMPLETED, lọc active + còn hàng, bù bán chạy cùng danh mục | N2-07, N1-07 | IT với dữ liệu đơn cố định: trả đúng thứ tự tần suất; mặt hàng hết hàng bị loại; thiếu thì bù đủ 4 |
| - [ ] N4-08 | 9–10 | QA tích hợp: test E2E nhiều module (đăng ký → đặt VNPay giả lập IPN → giao → hoàn tất → review → trả hàng → kiểm tồn kho, voucher, thông báo); rà collection Postman đủ mọi endpoint `api.md` | đa số task | Bộ E2E xanh trong CI; báo cáo endpoint thiếu/lệch gửi chủ module |

## 9. Lịch theo tuần (tổng hợp)

| Tuần | N1 | N2 | N3 | N4 |
|---|---|---|---|---|
| 3 | F-08 | F-06, F-07, seed catalog cho F-12 | F-01, F-02, F-03, F-05, F-10, F-12, F-14 | F-04, F-09, F-13 |
| 3 (cuối) | F-11 (cả nhóm) | F-11 | F-11 | F-11 (điều phối) |
| 4 | N1-01 → N1-03 | N2-01 → N2-04 | N3-01 → N3-04 | hỗ trợ test, F-13 |
| 5 | N1-04 → N1-08 | N2-05 → N2-12 | N3-05 → N3-07 | N4-01, N4-02 |
| 6 | N1-05, N1-09 → N1-11 | N2-13 → N2-17 | N3-08, N3-09 | N4-03 |
| 7 | N1-12, N1-13 | N2-18 → N2-21 | nối frontend auth/hồ sơ | N4-04, N4-05 |
| 8 | nối frontend giỏ/thanh toán | N2-22 → N2-27 | nối frontend admin user | N4-06, N4-07 |
| 9 | sửa lỗi, nối FE đơn hàng | N2-28 → N2-33 | N3-10 | N4-08 |
| 10 | M5: sửa lỗi theo E2E, đóng băng API | | | |

Người xong sớm nhận task "nối frontend" của module mình (thay mock bằng `services/<module>Service.ts`
theo `pages.md` cột API) hoặc hỗ trợ viết test cho người khác.

## 10. Rủi ro & cách giảm

| Rủi ro | Giảm thiểu |
|---|---|
| N1 bị chặn chờ InventoryService / PricingService / VoucherService | F-11 stub; N2 ưu tiên N2-03 (tuần 4), N2-13 và N2-16 (tuần 6) |
| Deadlock / bán vượt chỉ lộ khi chạy song song | N1-05 bắt buộc trước M2; mọi truy vấn khoá nhiều dòng có `ORDER BY id` |
| IPN VNPay không về khi dev | Làm N1-09/N1-10 tuần 6, test IPN bằng IT giả lập; ngrok chỉ cho N1-13 |
| Lệch contract FE ↔ BE | Mọi field theo `api.md`; đổi field phải sửa `api.md` trước + báo trong PR |
| Test chậm, không ổn định | 1 container dùng chung, TRUNCATE sau mỗi test, `MutableClock` thay cho `sleep` |
| Một người tụt tiến độ | Họp nhanh 2 lần/tuần đối chiếu ô tick; task nhỏ nên chuyển người dễ |

## 11. Trạng thái
- [ ] Giai đoạn 0 xong (M0)
- [ ] M1 · [ ] M2 · [ ] M3 · [ ] M4 · [ ] M5
- [ ] Đã cập nhật docs/ cho mọi thay đổi phát sinh
- [ ] Chuyển plan sang `completed/`
