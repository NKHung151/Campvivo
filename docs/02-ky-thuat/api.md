# API Contract v1

Nguồn chuẩn cho cả backend và frontend (`frontend/src/types/` phải khớp). Thêm/sửa endpoint →
cập nhật file này TRƯỚC khi code (skill `api-contract`). Quy tắc nghiệp vụ được tham chiếu bằng
mã BR-xx (`database.md` mục 4); FR-xx (`features.md`).

## 1. Quy ước chung

- Base path: `/api`. JSON UTF-8. Tên field camelCase.
- Xác thực: header `Authorization: Bearer <JWT>` (xem `authentication.md`). Cột "Quyền" bên dưới:
  `Public` = không cần token; `User` = mọi role đã đăng nhập; `Customer` = role CUSTOMER;
  `Staff` = EMPLOYEE hoặc ADMIN; `Admin` = ADMIN.
- Tiền: số nguyên VND (`450000`). Thời gian: ISO-8601 có múi giờ (`2026-10-17T09:15:00+07:00`).
  Ngày (tham số lọc): `yyyy-MM-dd`, hiểu theo giờ `Asia/Ho_Chi_Minh`.
- ID: số. Trang public dùng `slug`, trang admin/thao tác dùng `id`.
- Phân trang: query `page` (bắt đầu từ 1, mặc định 1), `limit` (mặc định 12, tối đa 100).

**Thành công**
```json
{ "success": true, "data": { ... } }
```
Danh sách có phân trang:
```json
{ "success": true, "data": [ ... ],
  "pagination": { "currentPage": 1, "limit": 12, "totalPages": 3, "totalItems": 25 } }
```
**Lỗi**
```json
{ "success": false, "errorCode": "OUT_OF_STOCK", "message": "Chỉ còn 3 sản phẩm khả dụng.",
  "details": { ... } }
```
- `message`: tiếng Việt, hiển thị được cho người dùng. `details`: tuỳ lỗi (vd. `fieldErrors`,
  `items` hết hàng, tổng tiền mới).
- Lỗi validate DTO → 400 `VALIDATION_ERROR`, `details.fieldErrors = { "email": "Email không hợp lệ" }`.
- Xử lý tập trung ở `common/exception/GlobalExceptionHandler`. Không trả stack trace.

## 2. Danh mục mã lỗi

| HTTP | errorCode | Khi nào |
|---|---|---|
| 400 | VALIDATION_ERROR | DTO không hợp lệ |
| 401 | UNAUTHORIZED | Thiếu/sai/hết hạn token, `tokenVersion` lệch |
| 403 | FORBIDDEN | Đúng token nhưng sai role |
| 404 | NOT_FOUND | Không tồn tại, hoặc thuộc user khác (không tiết lộ) |
| 409 | SLUG_ALREADY_EXISTS | Trùng slug |
| 500 | INTERNAL_ERROR | Lỗi hệ thống không lường trước (không trả chi tiết, chỉ ghi log phía server) |
| **auth/user** | | |
| 409 | EMAIL_ALREADY_EXISTS | Đăng ký email đã xác thực |
| 401 | INVALID_CREDENTIALS | Sai email/mật khẩu |
| 403 | EMAIL_NOT_VERIFIED | Đăng nhập khi chưa xác thực email |
| 403 | ACCOUNT_DISABLED | Tài khoản bị khoá |
| 400 | INVALID_OTP | Sai mã / không có mã đúng mục đích |
| 400 | OTP_EXPIRED | Mã hết hạn |
| 429 | OTP_TOO_MANY_ATTEMPTS | Sai quá số lần cho phép |
| 429 | OTP_RESEND_TOO_SOON | Gửi lại mã quá sớm |
| 400 | INVALID_CURRENT_PASSWORD | Đổi mật khẩu sai mật khẩu cũ |
| 400 | CANNOT_MODIFY_SELF | Admin tự khoá/hạ quyền mình |
| 502 | EMAIL_SEND_FAILED | Không gửi được email OTP |
| **catalog/inventory** | | |
| 404 | PARENT_CATEGORY_NOT_FOUND | |
| 400 | CIRCULAR_CATEGORY_DEPENDENCY | |
| 409 | CATEGORY_NOT_EMPTY | Xoá danh mục còn con/hàng |
| 400 | INVALID_STOCK_ADJUSTMENT | change = 0 hoặc làm stock < reserved |
| 502 | IMAGE_UPLOAD_FAILED | Lỗi khi đẩy ảnh lên Cloudinary (hoặc chưa cấu hình Cloudinary) |
| **cart/order/payment** | | |
| 400 | PRODUCT_INACTIVE | Mặt hàng ngừng kinh doanh |
| 409 | OUT_OF_STOCK | Vượt `available`; `details.items = [{type, id, name, availableQuantity}]` |
| 400 | CART_EMPTY | |
| 409 | PRICE_CHANGED | `expectedTotal` lệch; `details` = bảng giá mới (như response preview) |
| 400 | INVALID_VOUCHER | Không tồn tại / đã tắt |
| 400 | VOUCHER_EXPIRED | Ngoài thời hạn |
| 400 | VOUCHER_MIN_ORDER_NOT_MET | `details.minOrderValue` |
| 409 | VOUCHER_EXHAUSTED | Hết lượt |
| 409 | VOUCHER_ALREADY_USED | User đã dùng mã |
| 409 | VOUCHER_IN_USE | Xoá voucher đã có đơn dùng |
| 409 | ORDER_NOT_CANCELLABLE | Huỷ khi không PENDING |
| 409 | INVALID_STATUS_TRANSITION | Sai máy trạng thái (đơn hoặc yêu cầu trả hàng) |
| 502 | PAYMENT_GATEWAY_ERROR | Không tạo được URL thanh toán |
| 502 | SHIPPING_GATEWAY_ERROR | GHN lỗi/không kết nối được, chưa cấu hình `GHN_*`, hoặc địa chỉ không giao được (D-27) |
| **return/review** | | |
| 400 | RETURN_NOT_ALLOWED | Đơn không ở COMPLETED |
| 400 | RETURN_WINDOW_EXPIRED | Quá hạn trả |
| 409 | RETURN_ALREADY_EXISTS | |
| 403 | NOT_PURCHASED | Đánh giá khi chưa mua |
| 409 | REVIEW_ALREADY_EXISTS | |

## 3. Kiểu dữ liệu dùng chung (đặt trong `frontend/src/types/`)

```ts
type Money = number;                                   // VND, số nguyên
type ItemType = "PRODUCT" | "COMBO";
type Role = "CUSTOMER" | "EMPLOYEE" | "ADMIN";
type OrderStatus = "PENDING" | "CONFIRMED" | "SHIPPING" | "COMPLETED"
                 | "CANCELLED" | "PAYMENT_FAILED" | "RETURNED";
type PaymentMethod = "VNPAY" | "COD";
type PaymentStatus = "PENDING" | "SUCCESS" | "FAILED";
type ReturnStatus = "PENDING" | "APPROVED" | "REJECTED" | "COMPLETED";
type DiscountType = "PERCENTAGE" | "FIXED_AMOUNT";
type ArticleCategory = "BLOG" | "JOURNAL" | "GUIDE";
type NotificationType = "ORDER_STATUS" | "ORDER_NEW" | "RETURN_STATUS" | "RETURN_NEW" | "SUPPORT_MESSAGE";

interface UserProfile { id: number; email: string; fullName: string; phone: string | null;
  address: string | null; role: Role; }

interface ItemSummary {                                // sản phẩm hoặc combo trong danh sách
  type: ItemType; id: number; name: string; slug: string; primaryImageUrl: string | null;
  originalPrice: Money; price: Money;                  // price = giá sau chiến dịch (BR-6)
  availableQuantity: number; averageRating: number | null; reviewCount: number;
  brand?: string | null;                               // chỉ PRODUCT
}
```

## 4. Endpoints

### 4.1 auth — FR-01, FR-02, FR-03
| Method | Path | Quyền | Body → Response |
|---|---|---|---|
| POST | `/auth/register` | Public | `{email, password, fullName}` → 201 `{email}`; gửi OTP VERIFY_EMAIL |
| POST | `/auth/verify-email` | Public | `{email, code}` → 200 `{accessToken, user: UserProfile}` (đăng nhập luôn) |
| POST | `/auth/resend-code` | Public | `{email, purpose: "VERIFY_EMAIL" \| "RESET_PASSWORD"}` → 200 (luôn trả 200 nếu email không tồn tại) |
| POST | `/auth/login` | Public | `{email, password}` → 200 `{accessToken, user: UserProfile}` |
| POST | `/auth/logout` | User | → 200; tăng `token_version` |
| POST | `/auth/forgot-password` | Public | `{email}` → 200 (luôn 200); gửi OTP RESET_PASSWORD nếu email tồn tại |
| POST | `/auth/reset-password` | Public | `{email, code, newPassword}` → 200 |

Mật khẩu: 8–64 ký tự, có ít nhất 1 chữ và 1 số. `fullName` 2–100 ký tự.

Hành vi đã hiện thực (N3-02, khớp `features.md` FR-01 và BR-11):
- `register`: email **chưa xác thực** đăng ký lại → ghi đè mật khẩu + họ tên và gửi mã mới (không báo lỗi); nếu mã trước gửi
  cách chưa đủ 60 giây → `OTP_RESEND_TOO_SOON` và KHÔNG ghi đè. Gửi email lỗi → `EMAIL_SEND_FAILED`, không tạo tài khoản.
  Email đã xác thực → `EMAIL_ALREADY_EXISTS`. Mật khẩu quá 72 byte UTF-8 (giới hạn BCrypt; ký tự có dấu chiếm 2–3 byte)
  → `VALIDATION_ERROR` ở trường `password`. Họ tên được `trim` trước khi kiểm độ dài.
- `verify-email`: email không tồn tại và mã sai đều trả `INVALID_OTP` (chống dò email); tài khoản bị khoá → `ACCOUNT_DISABLED`.
  Mã dùng một lần; sai quá 5 lần → `OTP_TOO_MANY_ATTEMPTS` (phải gửi lại mã).
- `resend-code`: trả 200 và KHÔNG gửi gì khi email không tồn tại, tài khoản bị khoá, hoặc xin `VERIFY_EMAIL` cho email đã xác
  thực. Với email hợp lệ vẫn có thể `OTP_RESEND_TOO_SOON` (mã trước gửi < 60 giây) và `EMAIL_SEND_FAILED`.
- Ba endpoint này công khai nhưng backend vẫn từ chối request mang token sai/hết hạn (401); frontend phải gọi với
  `skipAuth` (xem `authentication.md` mục 3).

### 4.2 user — FR-04, FR-18, FR-20
| Method | Path | Quyền | Body → Response |
|---|---|---|---|
| GET | `/users/me` | User | → `UserProfile` |
| PUT | `/users/me` | User | `{fullName, phone, address}` → `UserProfile` |
| PUT | `/users/me/password` | User | `{currentPassword, newPassword}` → 200 (token cũ hết hiệu lực) |
| GET | `/users/me/reviews` | Customer | `?page&limit` → `[ReviewMine]` |
| GET | `/users/me/favorites` | Customer | `?type=PRODUCT\|COMBO\|ARTICLE&page&limit` → `[{id, type, item: ItemSummary \| ArticleSummary, createdAt}]` |

### 4.3 catalog / combo / article (public) — FR-07, FR-21a, FR-25
| Method | Path | Quyền | Query / Response |
|---|---|---|---|
| GET | `/categories` | Public | → cây `[{id, name, slug, children: [...]}]` |
| GET | `/products` | Public | `category` (slug, gồm danh mục con; slug không tồn tại → rỗng), `brand` (khớp đúng tên), `q` (tên/thương hiệu/slug, gõ không dấu được; có `q` thì xếp theo mức liên quan trước `sortBy`, như `/admin/products`), `slugs` (danh sách slug cách nhau dấu phẩy, ≤ 100 — khối sản phẩm chọn sẵn, đã xem, yêu thích), `minPrice`, `maxPrice` (theo giá sau chiến dịch), `sortBy=newest\|price_asc\|price_desc\|name_asc` (mặc định newest), `page`, `limit` (1..100) → `[ItemSummary]`. Chỉ sản phẩm đang bán |
| GET | `/products/{slug}` | Public | → `ProductDetail` |
| GET | `/products/{id}/recommendations` | Public | → tối đa 4 `ItemSummary` (FR-25) |
| GET | `/products/{id}/reviews` | Public | `?page&limit` → `{averageRating, reviewCount, items: [Review]}` |
| GET | `/brands` | Public | → `[{name, productCount}]` (distinct `products.brand`) |
| GET | `/combos` | Public | `category` (slug danh mục cấp 1), `q`, `sortBy=newest\|price_asc\|price_desc`, `page`, `limit` (1..100) → `[ComboSummary]` (chỉ combo đang bán) |
| GET | `/combos/{slug}` | Public | → `ComboDetail` |
| GET | `/combos/{id}/recommendations` | Public | → tối đa 4 `ItemSummary` |
| GET | `/combos/{id}/reviews` | Public | như sản phẩm |
| GET | `/search` | Public | `q` (bắt buộc, ≥ 2 ký tự), `type=PRODUCT\|COMBO\|ARTICLE` (bỏ trống = cả 3, mỗi loại tối đa `limit`), `page`, `limit` → `{products: [...], combos: [...], articles: [...]}` (+ `pagination` khi có `type`) |
| GET | `/campaigns/{slug}` | Public | → `{id, name, slug, description, bannerUrl, discountType, discountValue, startDate, endDate, products: [ItemSummary]}` (chỉ chiến dịch đang hiệu lực, ngược lại 404) |
| GET | `/articles` | Public | `category`, `page`, `limit` → `[ArticleSummary]` (chỉ đã xuất bản, mới nhất trước) |
| GET | `/articles/{slug}` | Public | → `ArticleDetail` |

```ts
interface ProductDetail extends ItemSummary {
  shortDescription: string | null; description: string | null; brand: string | null;
  specs: { name: string; value: string }[];                           // thông số theo thứ tự hiển thị
  category: { id: number; name: string; slug: string; path: {name: string; slug: string}[] };
  images: { url: string; isPrimary: boolean }[];
  campaign: { name: string; slug: string; endDate: string } | null;   // chiến dịch đang áp
  isFavorite?: boolean;                                                // khi có token
}
interface ComboSummary extends ItemSummary { itemsTotal: Money; itemCount: number;
  category: { id: number; name: string; slug: string }; }              // danh mục cấp 1 (D-23)
interface ComboDetail extends ItemSummary {
  description: string | null; category: {...};
  images: {...}[];      // ảnh tự tải lên (ảnh chính trước) rồi ảnh chính của các sản phẩm thành phần, bỏ trùng (D-26);
                        // ảnh đầu = primaryImageUrl
  itemsTotal: Money;                                                   // tổng giá lẻ hiện tại các món
  items: { product: ItemSummary; quantity: number }[];                 // chỉ hiển thị
  isFavorite?: boolean;
}
interface ArticleSummary { id: number; title: string; slug: string; summary: string | null;
  thumbnailUrl: string | null; category: ArticleCategory; publishedAt: string; }
interface ArticleDetail extends ArticleSummary { content: string; authorName: string; isFavorite?: boolean; }
interface Review { id: number; userFullName: string; rating: number; content: string | null;
  createdAt: string; updatedAt: string; }
interface ReviewMine extends Review { item: { type: ItemType; id: number; name: string; slug: string;
  primaryImageUrl: string | null } }
```

### 4.4 cart — FR-09
| Method | Path | Quyền | Body → Response |
|---|---|---|---|
| GET | `/cart` | Customer | → `Cart` |
| POST | `/cart/items` | Customer | `{productId?, comboId?, quantity}` (đúng 1 trong 2 id) → `Cart`. Đã có → cộng dồn |
| PATCH | `/cart/items/{id}` | Customer | `{quantity}` (≥ 1) → `Cart` |
| DELETE | `/cart/items/{id}` | Customer | → `Cart` |

```ts
interface Cart {
  items: { id: number; item: ItemSummary & { isActive: boolean }; quantity: number;
           lineTotal: Money; }[];                     // giá hiện hành (D-04)
  subtotal: Money;
  itemCount: number;                                  // tổng số món (badge giỏ hàng)
  hasProblems: boolean;                               // có dòng hết hàng/ngừng bán/vượt available
}
```
Lỗi: `OUT_OF_STOCK`, `PRODUCT_INACTIVE`, `NOT_FOUND`, `VALIDATION_ERROR` (số lượng 1..999; gửi cả hai hoặc không id nào →
`fieldErrors.exactlyOneItem`). Giỏ được tạo khi khách dùng lần đầu.

### 4.5 order / payment — FR-10, FR-11, FR-12, FR-19a
| Method | Path | Quyền | Body → Response |
|---|---|---|---|
| POST | `/vouchers/validate` | Customer | `{code}` → `{code, discountType, discountValue, discountAmount, ...OrderPreview}` (tính trên giỏ hiện tại, không trừ lượt) |
| POST | `/orders/preview` | Customer | `{voucherCode?, cartItemIds?, shippingDistrictId?, shippingWardCode?}` → `OrderPreview` (hiển thị trang thanh toán, lấy `expectedTotal`; có địa chỉ GHN thì tính phí ship — D-27; `cartItemIds` rỗng = cả giỏ) |
| POST | `/orders` | Customer | `CreateOrderRequest` → 201 `{orderId, orderCode, status, finalAmount, paymentMethod, paymentUrl: string \| null}` |
| GET | `/orders` | Customer | `?status&page&limit` (limit mặc định 10) → `[OrderSummary]` (của chính mình, mới nhất trước) |
| GET | `/orders/{id}` | Customer | → `OrderDetail` |
| POST | `/orders/{id}/cancel` | Customer | `{reason?}` (≤ 500) → `OrderDetail`. Chỉ khi PENDING (`ORDER_NOT_CANCELLABLE`); đơn người khác → `NOT_FOUND` |
| POST | `/orders/{id}/returns` | Customer | `{reason}` (10–1000 ký tự) → 201 `ReturnRequestDto` |
| GET | `/vnpay/ipn` | Public (VNPay) | query `vnp_*` → `{RspCode, Message}` theo chuẩn VNPay (BR-12, D-28) — KHÔNG bọc `{success, data}` |
| GET | `/vnpay/return` | Public | query `vnp_*` (trang `/payment/vnpay-return` chuyển nguyên) → `VnpayReturnResult`; cùng hàm xử lý với IPN (D-28) |

```ts
interface OrderPreview {
  items: { cartItemId: number; type: ItemType; id: number; name: string; quantity: number;
           originalPrice: Money; unitPrice: Money; lineTotal: Money }[];
  subtotal: Money; discountAmount: Money; shippingFee: Money; finalAmount: Money;
  voucherCode: string | null;
  shippingCalculated: boolean;                        // false = chưa có địa chỉ GHN, shippingFee tạm 0
  freeShipping: boolean; freeShippingThreshold: Money; // D-27
}
interface CreateOrderRequest {
  recipientName: string; recipientPhone: string;      // SĐT: 10 số, bắt đầu bằng 0
  shippingAddress: string;                            // địa chỉ đầy đủ (số nhà, phường, quận, tỉnh)
  shippingDistrictId: number; shippingWardCode: string; // mã GHN (api.md 4.12) — tính phí + tạo vận đơn
  saveAsDefault: boolean; paymentMethod: PaymentMethod; voucherCode?: string;
  note?: string; expectedTotal: Money;                // = OrderPreview.finalAmount client đang thấy
  cartItemIds?: number[];                             // dòng giỏ được chọn; rỗng = cả giỏ
}
type PaymentOutcome = "SUCCESS" | "FAILED" | "ALREADY_PROCESSED" | "INVALID_SIGNATURE" | "ORDER_NOT_FOUND" | "AMOUNT_MISMATCH";
interface VnpayReturnResult { outcome: PaymentOutcome; orderId: number | null; orderCode: string | null;
  orderStatus: OrderStatus | null; paymentStatus: PaymentStatus | null; }
interface OrderSummary { id: number; orderCode: string; status: OrderStatus; paymentMethod: PaymentMethod;
  finalAmount: Money; itemCount: number; firstItemName: string; firstItemImageUrl: string | null;
  createdAt: string; }
interface OrderDetail extends OrderSummary {
  items: { type: ItemType; itemId: number; name: string; imageUrl: string | null; originalPrice: Money;
           unitPrice: Money; quantity: number; lineTotal: Money; canReview: boolean }[];
  subtotal: Money; discountAmount: Money; shippingFee: Money; voucherCode: string | null;
  recipientName: string; recipientPhone: string; shippingAddress: string; note: string | null;
  payment: { method: PaymentMethod; status: PaymentStatus; paidAt: string | null };
  shipping: { carrier: "GHN"; districtId: number; wardCode: string;
              trackingCode: string | null;            // mã vận đơn GHN, null = chưa tạo (D-28)
              error: string | null };                 // lỗi lần tạo vận đơn gần nhất
  history: { oldStatus: OrderStatus | null; newStatus: OrderStatus; note: string | null; createdAt: string }[];
  returnRequest: ReturnRequestDto | null;
  canCancel: boolean; canReturn: boolean; completedAt: string | null;
}
interface ReturnRequestDto { id: number; orderId: number; reason: string; status: ReturnStatus;
  note: string | null; processedAt: string | null; createdAt: string; }
```
Ví dụ `POST /orders` VNPay:
```json
{ "recipientName": "Nguyễn Văn A", "recipientPhone": "0901234567",
  "shippingAddress": "12 Lê Lợi, Phường Bến Nghé, Quận 1, Hồ Chí Minh",
  "shippingDistrictId": 1442, "shippingWardCode": "20109", "saveAsDefault": true,
  "paymentMethod": "VNPAY", "voucherCode": "CAMPVIVO10", "expectedTotal": 840000 }
```
```json
{ "success": true, "data": { "orderId": 108, "orderCode": "ORD-20261017-7K2Q9A",
  "status": "PENDING", "finalAmount": 840000, "paymentMethod": "VNPAY",
  "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?..." } }
```
Lỗi `POST /orders`: `CART_EMPTY`, `OUT_OF_STOCK`, `PRODUCT_INACTIVE`, `PRICE_CHANGED`, các lỗi
voucher (BR-5), `PAYMENT_GATEWAY_ERROR`, `SHIPPING_GATEWAY_ERROR`, `VALIDATION_ERROR`, `NOT_FOUND` (cartItemIds không thuộc giỏ).
Chưa có module promotion: mọi `voucherCode` khác rỗng → `INVALID_VOUCHER` (`VoucherService` là điểm cắm khi làm FR-16).

### 4.6 review / favorite — FR-18, FR-20
| Method | Path | Quyền | Body → Response |
|---|---|---|---|
| POST | `/reviews` | Customer | `{productId?, comboId?, rating (1–5), content?}` → 201 `Review` |
| PUT | `/reviews/{id}` | Customer (chủ) | `{rating, content?}` → `Review` |
| DELETE | `/reviews/{id}` | Customer (chủ) | → 200 |
| POST | `/favorites` | Customer | `{productId? \| comboId? \| articleId?}` → 201 `{id}` (đã có → 200, trả id cũ) |
| DELETE | `/favorites/{id}` | Customer (chủ) | → 200 |
| DELETE | `/favorites?productId=\|comboId=\|articleId=` | Customer | → 200 (bỏ thích từ trang chi tiết) |

### 4.7 notification — FR-22
| Method | Path | Quyền | Body → Response |
|---|---|---|---|
| GET | `/notifications` | User | `?isRead=true\|false&page&limit` → `[{id, type, title, content, link, isRead, createdAt}]` + `unreadCount` trong `data` wrapper: `{items, unreadCount}` |
| PATCH | `/notifications/{id}/read` | User (chủ) | → 200 |
| PATCH | `/notifications/read-all` | User | → `{updated: number}` |

### 4.8 support chat — FR-26
| Method | Path | Quyền | Body → Response |
|---|---|---|---|
| GET | `/support/messages` | Customer | `?before=<id>&limit=30` → `[SupportMessage]` (hội thoại của chính mình, mới nhất trước) |
| POST | `/support/messages` | Customer | `{content}` (1–2000) → 201 `SupportMessage` |
| GET | `/admin/support/conversations` | Staff | `?page&limit` → `[{customer: {id, fullName, email}, lastMessage: SupportMessage}]` sắp theo tin mới nhất |
| GET | `/admin/support/conversations/{customerId}/messages` | Staff | `?before&limit` → `[SupportMessage]` |
| POST | `/admin/support/conversations/{customerId}/messages` | Staff | `{content}` → 201 `SupportMessage` (lưu `user_id = customerId`, `is_from_staff = true`) |

```ts
interface SupportMessage { id: number; customerId: number; content: string; isFromStaff: boolean; createdAt: string; }
```

### 4.9 WebSocket (STOMP) — FR-22, FR-26
- Endpoint: `/ws` (SockJS fallback). Header CONNECT: `Authorization: Bearer <JWT>`.
- Server đẩy (client SUBSCRIBE):
  - `/user/queue/notifications` → `{id, type, title, content, link, isRead, createdAt}`
  - `/user/queue/support` → `SupportMessage` (khách nhận tin của nhân viên; nhân viên nhận tin của mọi khách)
- Client chỉ gửi qua REST (POST ở 4.8); WebSocket chỉ để nhận. Mất kết nối → client gọi lại REST để đồng bộ.

### 4.10 admin
| Method | Path | Quyền | Ghi chú |
|---|---|---|---|
| GET | `/admin/users` | Admin | `?q` (email/họ tên), `role`, `isActive`, `page`, `limit` |
| GET | `/admin/users/{id}` | Admin | + số đơn, tổng chi tiêu (đơn COMPLETED) |
| PATCH | `/admin/users/{id}` | Admin | `{fullName?, phone?, role?, isActive?}`; đổi role/khoá → tăng token_version; `CANNOT_MODIFY_SELF` |
| GET | `/admin/categories` | Admin | cây `AdminCategoryNode[]` (+ `productCount`, `comboCount` gắn trực tiếp, `parentId`) |
| POST | `/admin/categories` | Admin | `CategoryRequest` → 201 `AdminCategoryNode`. Slug trống → sinh từ tên (thêm `-2`… nếu trùng); tự nhập mà trùng → `SLUG_ALREADY_EXISTS`; cha không tồn tại → `PARENT_CATEGORY_NOT_FOUND` |
| PUT | `/admin/categories/{id}` | Admin | `CategoryRequest` (slug trống = giữ slug cũ). Đổi cha: chọn chính nó/danh mục con → `CIRCULAR_CATEGORY_DEPENDENCY`; path cả cây con đổi theo (BR-13) |
| DELETE | `/admin/categories/{id}` | Admin | Còn danh mục con, sản phẩm hoặc combo → `CATEGORY_NOT_EMPTY` |
| GET | `/admin/products` | Admin | `?q&categoryId&brand&isActive&stock&sort&page&limit` → `[AdminProductSummary]`. `q`: tên, thương hiệu, slug; gõ không dấu được, mỗi từ khớp trọn từ trong slug hoặc thương hiệu ("leu 2 nguoi"; "ao" không khớp "AONIJIE"); từ khoá ≥ 4 ký tự còn khớp chuỗi con (mã hàng). Có `q` thì xếp theo mức liên quan trước (tên bắt đầu đúng từ khoá có dấu → tên chứa nguyên từ → slug bắt đầu bằng từ khoá), rồi mới theo `sort`. `categoryId` gồm danh mục con. `brand` khớp đúng tên (không phân biệt hoa thường). `stock`: `IN_STOCK` \| `LOW_STOCK` (khả dụng 1..`app.inventory.low-stock-threshold`) \| `OUT_OF_STOCK`. `sort`: `UPDATED_DESC` (mặc định) \| `CREATED_DESC` \| `NAME_ASC` \| `PRICE_ASC` \| `PRICE_DESC` \| `STOCK_ASC`. Giá trị sai → 400 |
| GET | `/admin/products/{id}` | Admin | |
| POST | `/admin/products` | Admin | `ProductRequest` (mục 4.11). Sản phẩm mới có tồn kho 0 (nhập kho ở FR-08) |
| PUT | `/admin/products/{id}` | Admin | như POST (thay toàn bộ ảnh và thông số; không đổi tồn kho) |
| PATCH | `/admin/products/{id}/status` | Admin | `{isActive}` → `AdminProductSummary`. Ẩn/hiện nhanh — "xoá" sản phẩm = ẩn (BR-14) |
| POST | `/admin/uploads/images` | Admin | multipart `file` (jpg/png/webp ≤ 5MB, kiểm tra theo nội dung file) + `?folder=products\|combos\|articles\|campaigns` → `{url}` (Cloudinary) |
| GET | `/admin/combos` | Admin | `?q&categoryId&isActive&sort&page&limit` → `[AdminComboSummary]`. `q`: tên, slug (gõ không dấu được). `categoryId` gồm danh mục con. `sort`: `UPDATED_DESC` (mặc định) \| `CREATED_DESC` \| `NAME_ASC` \| `PRICE_ASC` \| `PRICE_DESC` \| `STOCK_ASC` |
| POST | `/admin/combos` | Admin | `ComboRequest` → 201 `AdminComboDetail`; tồn kho bắt đầu 0 (đóng gói ở `/pack`). `categoryId` phải là danh mục cấp 1 (D-23), không thì 400 `fieldErrors.categoryId`. < 2 sản phẩm hoặc trùng sản phẩm → 400 `fieldErrors.items`; sản phẩm không tồn tại → `NOT_FOUND`; slug tự nhập trùng → `SLUG_ALREADY_EXISTS` |
| GET/PUT | `/admin/combos/{id}` | Admin | PUT như POST (thay toàn bộ thành phần và ảnh; slug trống = giữ slug cũ) |
| PATCH | `/admin/combos/{id}/status` | Admin | `{isActive}` → `AdminComboSummary`. "Xoá" combo = ẩn (BR-14) |
| POST | `/admin/combos/{id}/pack` | Admin | `{sets: 1..999}` → `AdminComboDetail`. Trừ `sets × quantity` ở kho sản phẩm của từng dòng, cộng `sets` vào kho combo (D-22). Thiếu → 409 `OUT_OF_STOCK` (`details.shortages`) |
| POST | `/admin/combos/{id}/unpack` | Admin | `{sets}` → `AdminComboDetail`. Trả hàng về kho sản phẩm; `sets` > số bộ khả dụng → `INVALID_STOCK_ADJUSTMENT` |
| GET | `/admin/inventory` | Staff | `?type=PRODUCT\|COMBO&q&lowStock=true&page&limit` → `[{type, id, name, stockQuantity, reservedQuantity, availableQuantity, isLowStock}]` |
| POST | `/admin/inventory/adjustments` | Staff | `{productId? \| comboId?, changeQuantity, reason: IMPORT\|EXPORT\|ADJUST, note?}` → log mới |
| GET | `/admin/inventory/logs` | Staff | `?productId&comboId&from&to&page&limit` |
| GET | `/admin/orders` | Staff | `?status&paymentMethod&q&from&to&page&limit` (limit mặc định 20) → `[AdminOrderSummary]`, mới nhất trước. `q`: mã đơn, SĐT người nhận, email/họ tên khách; `from`/`to` `yyyy-MM-dd` theo giờ VN (gồm cả ngày `to`) |
| GET | `/admin/orders/{id}` | Staff | → `AdminOrderDetail` (`OrderDetail` + khách + `allowedStatuses` + `canCreateShipment`) |
| PATCH | `/admin/orders/{id}/status` | Staff | `{status, note?}` → `{orderId, oldStatus, newStatus, updatedAt}`. Nhân viên chỉ được: PENDING+COD → CONFIRMED / CANCELLED (bắt buộc `note`, thiếu → 400 `fieldErrors.note`); CONFIRMED → SHIPPING (không ghi chú thì tự ghi mã vận đơn GHN); SHIPPING → COMPLETED. Khác → `INVALID_STATUS_TRANSITION` |
| POST | `/admin/orders/{id}/shipment` | Staff | Tạo (lại) vận đơn GHN cho đơn CONFIRMED/SHIPPING chưa có vận đơn → `AdminOrderDetail`; đã có thì giữ nguyên; lỗi GHN → `SHIPPING_GATEWAY_ERROR` (lỗi được lưu vào `shipping.error`) — D-28 |
| GET | `/admin/orders/{id}/shipment` | Staff | Hành trình vận đơn đọc từ GHN → `ShipmentTracking`; chưa có vận đơn → `NOT_FOUND` |
| GET | `/admin/returns` | Staff | `?status&page&limit` |
| GET | `/admin/returns/{id}` | Staff | + `OrderDetail` |
| PATCH | `/admin/returns/{id}` | Staff | `{action: "APPROVE" \| "REJECT" \| "COMPLETE", note?}` (REJECT bắt buộc note) |
| GET/POST | `/admin/vouchers` | Admin | POST `{code, discountType, discountValue, maxDiscount?, minOrderValue, quantity, startDate, expiryDate, isActive}` |
| GET/PUT/DELETE | `/admin/vouchers/{id}` | Admin | DELETE → `VOUCHER_IN_USE` nếu đã dùng |
| GET/POST | `/admin/campaigns` | Admin | POST `{name, slug?, description?, bannerUrl?, discountType, discountValue, startDate, endDate, isActive, productIds: number[]}` |
| GET/PUT/DELETE | `/admin/campaigns/{id}` | Admin | |
| GET/POST | `/admin/articles` | Admin | `?category&isPublished&q`; POST `{title, slug?, summary?, content, thumbnailUrl?, category, isPublished}` |
| GET/PUT/DELETE | `/admin/articles/{id}` | Admin | |
| GET | `/admin/analytics/summary` | Admin | `?from&to` → `{revenue, completedOrders, ordersByStatus: {...}, newCustomers}` |
| GET | `/admin/analytics/revenue` | Admin | `?from&to&groupBy=day\|month\|year` → `[{period: "2026-10-17" \| "2026-10" \| "2026", revenue, orderCount}]` |
| GET | `/admin/analytics/top-items` | Admin | `?from&to&limit=10` → `[{type, id, name, quantitySold, revenue}]` |

Kiểu dữ liệu đơn hàng admin (định nghĩa đầy đủ ở `frontend/src/types/api/order.ts`):
```ts
interface AdminOrderCustomer { id: number; email: string; fullName: string; phone: string | null; }
interface AdminOrderSummary extends OrderSummary { paymentStatus: PaymentStatus | null; customer: AdminOrderCustomer | null;
  recipientName: string; recipientPhone: string; shippingOrderCode: string | null; }
interface AdminOrderDetail { order: OrderDetail; customer: AdminOrderCustomer | null;
  allowedStatuses: OrderStatus[];        // nút chuyển trạng thái được phép ngay lúc này
  canCreateShipment: boolean; }          // CONFIRMED/SHIPPING chưa có vận đơn
interface ShipmentTracking { carrier: "GHN"; trackingCode: string; status: string;   // mã trạng thái GHN (ready_to_pick, delivering...)
  expectedDeliveryTime: string | null; updatedAt: string | null; log: { status: string; updatedAt: string | null }[]; }
```

### 4.11 Kiểu dữ liệu admin catalog
Định nghĩa đầy đủ trong `frontend/src/types/api/catalog.ts` (nguồn TypeScript); backend trả đúng field:
```ts
interface AdminCategoryNode { id: number; name: string; slug: string; parentId: number | null;
  productCount: number; comboCount: number; children: AdminCategoryNode[]; }
interface CategoryRequest { name: string; slug?: string; parentId?: number | null; }   // name ≤ 150, slug ≤ 180
interface AdminProductSummary { id: number; name: string; slug: string; brand: string | null;
  categoryId: number; categoryName: string; categoryPath: string[]; price: Money;
  stockQuantity: number; reservedQuantity: number;
  availableQuantity: number;                         // stock − reserved (BR-1)
  isActive: boolean; primaryImageUrl: string | null; updatedAt: string; }
interface ProductSpec { name: string; value: string; }
interface AdminProductImage { url: string; isPrimary: boolean; displayOrder: number; }
interface AdminProductDetail extends AdminProductSummary { shortDescription: string | null;
  description: string | null; specs: ProductSpec[]; images: AdminProductImage[]; createdAt: string; }
interface ProductRequest { name: string; slug?: string; shortDescription?: string | null;   // ≤ 500
  description?: string | null; brand?: string | null;
  price: Money; categoryId: number; isActive: boolean;
  specs: ProductSpec[];                     // ≤ 30 dòng; tên trùng (không phân biệt hoa thường) bị gộp
  images: AdminProductImage[]; }   // thay toàn bộ ảnh, đúng 1 ảnh isPrimary
interface AdminComboSummary { id: number; name: string; slug: string; categoryId: number; categoryName: string;
  categoryPath: string[]; comboPrice: Money; itemsTotal: Money;   // itemsTotal = tổng giá lẻ hiện tại (giá × số lượng)
  itemCount: number; stockQuantity: number; reservedQuantity: number; availableQuantity: number;   // tồn kho theo bộ (D-03)
  isActive: boolean; primaryImageUrl: string | null; updatedAt: string; }
interface AdminComboDetail extends Omit<AdminComboSummary, "categoryPath" | "itemCount"> { description: string | null;
  maxPackable: number;   // số bộ đóng gói được ngay = min(khả dụng sản phẩm ÷ quantity)
  items: { productId: number; productName: string; productSlug: string; productImageUrl: string | null;
           productPrice: Money; productActive: boolean; productAvailableQuantity: number; quantity: number }[];
  images: AdminProductImage[];   // ảnh tự tải lên (có thể rỗng)
  createdAt: string; }   // primaryImageUrl = ảnh chính tự tải lên, chưa có thì ảnh sản phẩm thành phần đầu tiên (D-26)
interface ComboRequest { name: string; slug?: string; description?: string | null; categoryId: number;
  comboPrice: Money; isActive: boolean;
  images: AdminProductImage[];   // ảnh tự tải lên ≤ 10, có ảnh thì đúng 1 ảnh chính; không dùng URL của mặt hàng khác
  items: { productId: number; quantity: number }[]; }
  // 2..20 sản phẩm khác nhau, quantity 1..99.
  // Combo còn bộ đã đóng gói thì không đổi được thành phần (400 fieldErrors.items)
```

### 4.12 shipping (GHN) — FR-10, D-27
Token/ShopId GHN chỉ ở backend (`app.ghn.*` ← biến `GHN_*`); frontend chỉ gọi các endpoint dưới đây.
| Method | Path | Quyền | Query / Body → Response |
|---|---|---|---|
| GET | `/shipping/provinces` | Public | → `[Province]` (bỏ tỉnh tắt/tỉnh thử nghiệm của staging; xếp theo tên tiếng Việt; cache server `app.ghn.master-data-cache-ttl`) |
| GET | `/shipping/districts` | Public | `provinceId` (> 0) → `[District]` |
| GET | `/shipping/wards` | Public | `districtId` (> 0) → `[Ward]` |
| GET | `/shipping/services` | Public | `toDistrictId` → `[ShippingServiceOption]` — gói GHN khả dụng từ kho tới quận nhận (tham khảo; đơn dùng gói `app.ghn.service-type-id`) |
| POST | `/shipping/fee` | Public | `ShippingFeeRequest` → `ShippingFee`. Tính thử ở trang thanh toán; khi tạo đơn server tự tính lại từ giỏ |

```ts
interface Province { id: number; name: string; }
interface District { id: number; name: string; }
interface Ward { code: string; name: string; }
interface ShippingServiceOption { serviceId: number; serviceTypeId: number; name: string; }
interface ShippingFeeRequest {
  toDistrictId: number; toWardCode: string;
  quantity: number;          // tổng số món (1..999); cân nặng = quantity × app.ghn.default-weight (gram)
  orderValue?: Money;        // tạm tính − giảm giá, để xét miễn phí ship
}
interface ShippingFee {
  fee: Money;                // phí khách trả: 0 nếu orderValue ≥ freeShippingThreshold
  carrierFee: Money;         // phí GHN báo (shop trả GHN)
  freeShipping: boolean; freeShippingThreshold: Money;
}
```
Lỗi: `VALIDATION_ERROR` (`fieldErrors`), `SHIPPING_GATEWAY_ERROR` (502).

## 5. Ánh xạ sang frontend
- Mỗi nhóm endpoint ↔ `frontend/src/services/<module>Service.ts` (auth, user, catalog, cart, order,
  review, favorite, notification, support, admin…). Component không gọi `fetch/axios` trực tiếp.
- Client HTTP chung `services/apiClient.ts` (axios): gắn token, bóc `data`, chuyển lỗi thành
  `ApiError { errorCode, message, details }`; 401 → xoá token, chuyển về đăng nhập.
