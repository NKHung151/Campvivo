# Thiết kế cơ sở dữ liệu

Nguồn logic: báo cáo Phase1_v4, bảng 2.12 (22 lớp/bảng). File này là **đặc tả vật lý** — migration
Flyway và JPA entity phải khớp 100%. Đổi schema → sửa file này trước (skill `database-design`).
Lý do các chỗ bổ sung so với báo cáo: `docs/99-ghi-chu/decisions.md` (D-02…D-17).

## 1. Quy ước chung

- PostgreSQL 15+. Tên bảng snake_case **số nhiều**, cột snake_case. Entity Java số ít, map bằng
  `@Table(name = "...")` / `@Column(name = "...")`.
- Khoá chính: `id BIGINT GENERATED ALWAYS AS IDENTITY` (trừ `discount_campaign_products` dùng khoá
  ghép). FK đặt tên `<thực thể>_id` (vd. `user_id`, `combo_id`).
- Mọi bảng có `created_at timestamptz NOT NULL DEFAULT now()`; bảng có sửa đổi có thêm
  `updated_at timestamptz NOT NULL DEFAULT now()` (cập nhật ở tầng JPA `@PreUpdate`).
- Tiền: `NUMERIC(19,0)` (VND, không có phần lẻ) ↔ Java `BigDecimal` (scale 0) ↔ JSON số nguyên.
  Làm tròn mọi phép tính tiền về số nguyên bằng `RoundingMode.HALF_UP`.
- Enum: cột `VARCHAR(30)` + `CHECK (col IN (...))`; Java `@Enumerated(EnumType.STRING)`.
- Boolean: `BOOLEAN NOT NULL DEFAULT ...`.
- Chuỗi ngắn `VARCHAR(255)` trừ khi ghi khác; nội dung dài `TEXT`.
- Ký hiệu trong bảng cột: **U** = unique, **N** = nullable (không ghi = NOT NULL),
  **(+)** = cột bổ sung so với báo cáo.
- Migration: `backend/src/main/resources/db/migration/V<n>__<mo_ta>.sql`. `V1__users.sql` (F-05) là
  migration đầu tiên, `V2__catalog_schema.sql` (F-06) tạo 5 bảng catalog (mục 3.2–3.6), `V5__order_schema.sql` (F-08) tạo 7 bảng
mua hàng (mục 3.7–3.13; V3/V4 là seed); các bảng còn lại được thêm dần bằng migration mới theo
  `.agents/plans/active/backend-roadmap.md` (F-06 → F-09). Từ V1 trở đi KHÔNG sửa migration đã merge
  (decisions D-14).

## 2. Ánh xạ báo cáo → bảng vật lý

| # | Lớp (báo cáo) | Bảng | Module backend | Cột/FK bổ sung chính (+) |
|---|---|---|---|---|
| 1 | User | `users` | auth, user | email_verified, OTP (5 cột), token_version, is_active |
| 2 | Category | `categories` | catalog | — |
| 3 | Product | `products` | catalog | brand, short_description, specs |
| 4 | Image | `images` | catalog | — |
| 5 | ProductCombo | `product_combos` | combo | slug, description, stock_quantity, reserved_quantity |
| 6 | ComboItem | `combo_items` | combo | — |
| 7 | Cart | `carts` | cart | — |
| 8 | CartItem | `cart_items` | cart | (không có cột giá — D-04) |
| 9 | Order | `orders` | order | voucher_code, subtotal, shipping_fee, recipient_*, payment_method, completed_at |
| 10 | OrderItem | `order_items` | order | combo_id, item_name, item_image_url, original_price, line_total |
| 11 | OrderStatusHistory | `order_status_history` | order | changed_by |
| 12 | Payment | `payments` | payment | amount, paid_at |
| 13 | ReturnRequest | `return_requests` | order | processed_by |
| 14 | Voucher | `vouchers` | promotion | max_discount, used_count, start_date, is_active |
| 15 | DiscountCampaign | `discount_campaigns` | promotion | slug, description, banner_url, is_active |
| 16 | DiscountCampaignProduct | `discount_campaign_products` | promotion | — |
| 17 | Review | `reviews` | review | — |
| 18 | Favorite | `favorites` | favorite | article_id |
| 19 | SupportMessage | `support_messages` | support | is_from_staff (D-05) |
| 20 | Notification | `notifications` | notification | type, link |
| 21 | Article | `articles` | article | summary, category, is_published, published_at |
| 22 | InventoryLog | `inventory_logs` | inventory | combo_id |

## 3. Đặc tả bảng

### 3.1 `users`
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| email | VARCHAR(255) | U, lưu chữ thường (normalize `trim().toLowerCase()`) |
| password_hash | VARCHAR(100) | BCrypt (strength 10) |
| full_name | VARCHAR(100) | |
| phone | VARCHAR(20) | N |
| address | VARCHAR(500) | N — địa chỉ mặc định (D-02) |
| role | VARCHAR(30) | `CUSTOMER` \| `EMPLOYEE` \| `ADMIN`, mặc định `CUSTOMER` |
| is_active | BOOLEAN | (+) mặc định true; false = bị khoá |
| email_verified | BOOLEAN | (+) mặc định false |
| otp_code_hash | VARCHAR(100) | (+) N — BCrypt của OTP |
| otp_purpose | VARCHAR(30) | (+) N — `VERIFY_EMAIL` \| `RESET_PASSWORD` |
| otp_expires_at | timestamptz | (+) N |
| otp_attempts | INT | (+) mặc định 0 |
| otp_last_sent_at | timestamptz | (+) N — chống gửi lại liên tục |
| token_version | INT | (+) mặc định 0 (D-11) |
| created_at, updated_at | timestamptz | |

### 3.2 `categories`
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| parent_id | BIGINT | N, FK → categories(id) `ON DELETE RESTRICT` |
| name | VARCHAR(150) | |
| slug | VARCHAR(180) | U |
| path | VARCHAR(500) | Đường dẫn id từ gốc, dạng `/1/5/12/` (gồm chính nó). Index `varchar_pattern_ops` |
| created_at, updated_at | timestamptz | |

### 3.3 `products`
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| category_id | BIGINT | FK → categories(id) |
| name | VARCHAR(255) | |
| slug | VARCHAR(280) | U |
| short_description | VARCHAR(500) | (+) N — 1–2 câu dưới tên sản phẩm |
| description | TEXT | N (HTML đã sanitize) |
| brand | VARCHAR(100) | (+) N (D-16) |
| price | NUMERIC(19,0) | `CHECK (price > 0)` |
| specs | JSONB | (+) mặc định `'[]'`, mảng `[{name, value}]` có thứ tự (≤ 30 dòng) |
| stock_quantity | INT | mặc định 0 |
| reserved_quantity | INT | mặc định 0 (báo cáo ghi `reserveQuantity` — dùng tên này) |
| is_active | BOOLEAN | mặc định true |
| created_at, updated_at | timestamptz | |

`CHECK (stock_quantity >= 0 AND reserved_quantity >= 0 AND reserved_quantity <= stock_quantity)`.
Không có biến thể (D-24): mỗi màu/size cần bán và quản lý tồn riêng là một sản phẩm riêng.

### 3.4 `images`
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| product_id | BIGINT | N, FK → products(id) `ON DELETE CASCADE` |
| combo_id | BIGINT | N, FK → product_combos(id) `ON DELETE CASCADE` |
| image_url | VARCHAR(500) | U |
| is_primary | BOOLEAN | mặc định false; mỗi mặt hàng tối đa 1 ảnh chính |
| display_order | INT | mặc định 0 |
| created_at | timestamptz | |

`CHECK (num_nonnulls(product_id, combo_id) = 1)`;
unique một phần: `(product_id) WHERE is_primary AND product_id IS NOT NULL`, tương tự combo.

### 3.5 `product_combos`
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| category_id | BIGINT | FK → categories(id) — chỉ danh mục cấp 1 (D-23, kiểm tra ở service) |
| name | VARCHAR(255) | |
| slug | VARCHAR(280) | (+) U |
| description | TEXT | (+) N |
| combo_price | NUMERIC(19,0) | `CHECK (combo_price > 0)` |
| stock_quantity | INT | (+) mặc định 0 (D-03) |
| reserved_quantity | INT | (+) mặc định 0 |
| is_active | BOOLEAN | mặc định true |
| created_at, updated_at | timestamptz | |

`CHECK` tồn kho giống `products`.

### 3.6 `combo_items` — thành phần của một bộ combo (hiển thị + đóng gói, D-22)
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| combo_id | BIGINT | FK → product_combos(id) `ON DELETE CASCADE` |
| product_id | BIGINT | FK → products(id) |
| quantity | INT | `CHECK (quantity > 0)` — số cái trong một bộ |

Unique `(combo_id, product_id)`.

### 3.7 `carts`
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| user_id | BIGINT | U, FK → users(id) — 1 giỏ/user, tạo khi đăng ký |
| created_at, updated_at | timestamptz | |

### 3.8 `cart_items`
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| cart_id | BIGINT | FK → carts(id) `ON DELETE CASCADE` |
| product_id | BIGINT | N, FK → products(id) |
| combo_id | BIGINT | N, FK → product_combos(id) |
| quantity | INT | `CHECK (quantity > 0)` |
| created_at, updated_at | timestamptz | |

`CHECK (num_nonnulls(product_id, combo_id) = 1)`; unique một phần
`(cart_id, product_id) WHERE product_id IS NOT NULL` và `(cart_id, combo_id) WHERE combo_id IS NOT NULL`.
**Không có cột giá** (D-04).

### 3.9 `orders`
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| order_code | VARCHAR(30) | U — `ORD-yyyyMMdd-XXXXXX` (6 ký tự A-Z0-9 ngẫu nhiên, sinh lại nếu trùng); dùng làm `vnp_TxnRef` |
| user_id | BIGINT | FK → users(id) |
| voucher_id | BIGINT | N, FK → vouchers(id) |
| voucher_code | VARCHAR(50) | (+) N — snapshot mã |
| subtotal | NUMERIC(19,0) | (+) Σ `order_items.line_total` |
| discount_amount | NUMERIC(19,0) | mặc định 0 — giảm từ voucher |
| shipping_fee | NUMERIC(19,0) | (+) mặc định 0 (D-17) |
| final_amount | NUMERIC(19,0) | `= subtotal − discount_amount + shipping_fee` |
| recipient_name | VARCHAR(100) | (+) snapshot |
| recipient_phone | VARCHAR(20) | (+) snapshot |
| shipping_address | VARCHAR(500) | snapshot — địa chỉ đầy đủ (số nhà, phường, quận, tỉnh) |
| shipping_district_id | INT | (+) mã quận GHN (D-27) |
| shipping_ward_code | VARCHAR(20) | (+) mã phường GHN |
| shipping_order_code | VARCHAR(50) | (+) U, N — mã vận đơn GHN; NULL = chưa tạo (D-28) |
| shipping_order_error | VARCHAR(500) | (+) N — lỗi lần tạo vận đơn gần nhất (để tạo lại) |
| payment_method | VARCHAR(30) | (+) `VNPAY` \| `COD` (MoMo thêm sau) |
| status | VARCHAR(30) | `PENDING` \| `CONFIRMED` \| `SHIPPING` \| `COMPLETED` \| `CANCELLED` \| `PAYMENT_FAILED` \| `RETURNED` |
| note | VARCHAR(500) | N — ghi chú của khách |
| completed_at | timestamptz | (+) N — thời điểm chuyển COMPLETED; dùng cho thời hạn trả hàng và thống kê doanh thu |
| created_at, updated_at | timestamptz | |

`CHECK (subtotal >= 0 AND discount_amount >= 0 AND discount_amount <= subtotal AND shipping_fee >= 0 AND
final_amount = subtotal − discount_amount + shipping_fee)`. `voucher_id` chưa có FK cho tới khi migration promotion (F-07) tạo
bảng `vouchers` (migration đó thêm FK).

### 3.10 `order_items`
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| order_id | BIGINT | FK → orders(id) `ON DELETE CASCADE` |
| product_id | BIGINT | N, FK → products(id) |
| combo_id | BIGINT | (+) N, FK → product_combos(id) |
| item_name | VARCHAR(255) | (+) snapshot tên lúc đặt |
| item_image_url | VARCHAR(500) | (+) N — snapshot ảnh chính |
| original_price | NUMERIC(19,0) | (+) giá gốc lúc đặt (product.price / combo_price) |
| unit_price | NUMERIC(19,0) | giá thực trả mỗi đơn vị (sau chiến dịch) |
| quantity | INT | `CHECK (quantity > 0)` |
| line_total | NUMERIC(19,0) | (+) `= unit_price × quantity` |

`CHECK (num_nonnulls(product_id, combo_id) = 1)`. Không bao giờ sửa sau khi tạo (BR-4).

### 3.11 `order_status_history`
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| order_id | BIGINT | FK → orders(id) `ON DELETE CASCADE` |
| old_status | VARCHAR(30) | N (NULL khi tạo đơn) |
| new_status | VARCHAR(30) | |
| changed_by | BIGINT | (+) N, FK → users(id) — NULL = System (job, IPN) |
| note | VARCHAR(500) | N |
| created_at | timestamptz | |

### 3.12 `payments`
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| order_id | BIGINT | U, FK → orders(id) — 1 bản ghi/đơn (không hỗ trợ thanh toán lại) |
| method | VARCHAR(30) | `VNPAY` \| `COD` |
| amount | NUMERIC(19,0) | (+) = `orders.final_amount` |
| status | VARCHAR(30) | `PENDING` \| `SUCCESS` \| `FAILED` |
| transaction_id | VARCHAR(100) | U, N — `vnp_TransactionNo`; NULL với COD |
| payload | JSONB | N — toàn bộ tham số IPN (đã bỏ `vnp_SecureHash`) |
| paid_at | timestamptz | (+) N |
| created_at, updated_at | timestamptz | |

Tạo cùng lúc với đơn (`PENDING`). COD chuyển `SUCCESS` khi đơn COMPLETED.

### 3.13 `return_requests`
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| order_id | BIGINT | U, FK → orders(id) — 1 yêu cầu/đơn |
| reason | VARCHAR(1000) | |
| status | VARCHAR(30) | `PENDING` \| `APPROVED` \| `REJECTED` \| `COMPLETED` |
| processed_by | BIGINT | (+) N, FK → users(id) — nhân viên xử lý gần nhất |
| processed_at | timestamptz | N |
| note | VARCHAR(1000) | N — ghi chú của nhân viên (bắt buộc khi REJECTED) |
| created_at, updated_at | timestamptz | |

### 3.14 `vouchers`
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| code | VARCHAR(50) | U, lưu IN HOA |
| discount_type | VARCHAR(30) | `PERCENTAGE` \| `FIXED_AMOUNT` |
| discount_value | NUMERIC(19,0) | PERCENTAGE: 1–100; FIXED_AMOUNT: > 0 |
| max_discount | NUMERIC(19,0) | (+) N — trần giảm cho PERCENTAGE |
| min_order_value | NUMERIC(19,0) | mặc định 0 |
| quantity | INT | số lượng phát hành, > 0 |
| used_count | INT | (+) mặc định 0 |
| start_date | timestamptz | (+) |
| expiry_date | timestamptz | `CHECK (expiry_date > start_date)` |
| is_active | BOOLEAN | (+) mặc định true |
| created_at, updated_at | timestamptz | |

`CHECK (used_count >= 0 AND used_count <= quantity)`.

### 3.15 `discount_campaigns`
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| name | VARCHAR(255) | |
| slug | VARCHAR(280) | (+) U — trang `/promotions/[slug]` |
| description | TEXT | (+) N |
| banner_url | VARCHAR(500) | (+) N |
| discount_type | VARCHAR(30) | `PERCENTAGE` \| `FIXED_AMOUNT` |
| discount_value | NUMERIC(19,0) | như voucher |
| start_date | timestamptz | |
| end_date | timestamptz | `CHECK (end_date > start_date)` |
| is_active | BOOLEAN | (+) mặc định true |
| created_at, updated_at | timestamptz | |

### 3.16 `discount_campaign_products`
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| campaign_id | BIGINT | FK → discount_campaigns(id) `ON DELETE CASCADE` |
| product_id | BIGINT | FK → products(id) `ON DELETE CASCADE` |

PK `(campaign_id, product_id)`. Chiến dịch chỉ áp cho sản phẩm, không áp cho combo.

### 3.17 `reviews`
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| user_id | BIGINT | FK → users(id) |
| product_id | BIGINT | N, FK → products(id) |
| combo_id | BIGINT | N, FK → product_combos(id) |
| rating | SMALLINT | `CHECK (rating BETWEEN 1 AND 5)` |
| content | VARCHAR(2000) | N |
| created_at, updated_at | timestamptz | |

`CHECK (num_nonnulls(product_id, combo_id) = 1)`; unique một phần `(user_id, product_id) WHERE
product_id IS NOT NULL`, `(user_id, combo_id) WHERE combo_id IS NOT NULL`. Xoá cứng (D-09).

### 3.18 `favorites`
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| user_id | BIGINT | FK → users(id) `ON DELETE CASCADE` |
| product_id | BIGINT | N, FK → products(id) `ON DELETE CASCADE` |
| combo_id | BIGINT | N, FK → product_combos(id) `ON DELETE CASCADE` |
| article_id | BIGINT | (+) N, FK → articles(id) `ON DELETE CASCADE` |
| created_at | timestamptz | |

`CHECK (num_nonnulls(product_id, combo_id, article_id) = 1)`; unique một phần theo từng loại.

### 3.19 `support_messages` (D-05)
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| user_id | BIGINT | FK → users(id) — **luôn là khách hàng** (chủ hội thoại), kể cả tin nhân viên gửi |
| content | VARCHAR(2000) | |
| is_from_staff | BOOLEAN | (+) mặc định false; true = tin do Employee/Admin gửi |
| created_at | timestamptz | |

Hội thoại của khách X: `WHERE user_id = X ORDER BY created_at`. Danh sách hội thoại cho nhân viên:
nhóm theo `user_id`, sắp theo `max(created_at)` giảm dần. Không lưu nhân viên nào trả lời, không có
trạng thái đã đọc.

### 3.20 `notifications`
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| user_id | BIGINT | FK → users(id) `ON DELETE CASCADE` — người nhận |
| type | VARCHAR(30) | (+) `ORDER_STATUS` \| `ORDER_NEW` \| `RETURN_STATUS` \| `RETURN_NEW` \| `SUPPORT_MESSAGE` |
| title | VARCHAR(255) | |
| content | VARCHAR(1000) | |
| link | VARCHAR(500) | (+) N — route frontend, vd. `/account/orders/108` |
| is_read | BOOLEAN | mặc định false |
| created_at | timestamptz | |

### 3.21 `articles`
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| user_id | BIGINT | FK → users(id) — tác giả (Admin) |
| title | VARCHAR(255) | |
| slug | VARCHAR(280) | U |
| summary | VARCHAR(1000) | (+) N |
| content | TEXT | HTML đã sanitize |
| thumbnail_url | VARCHAR(500) | N |
| category | VARCHAR(30) | (+) `BLOG` \| `JOURNAL` \| `GUIDE` (D-16) |
| is_published | BOOLEAN | (+) mặc định false |
| published_at | timestamptz | (+) N — gán lần đầu xuất bản |
| created_at, updated_at | timestamptz | |

### 3.22 `inventory_logs`
| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK |
| product_id | BIGINT | N, FK → products(id) |
| combo_id | BIGINT | (+) N, FK → product_combos(id) |
| user_id | BIGINT | FK → users(id) — người thực hiện |
| change_quantity | INT | khác 0; dương = nhập, âm = xuất |
| stock_after | INT | `stock_quantity` sau thay đổi |
| reason | VARCHAR(30) | `IMPORT` \| `EXPORT` \| `ADJUST` \| `RETURN` |
| note | VARCHAR(500) | N |
| created_at | timestamptz | |

`CHECK (num_nonnulls(product_id, combo_id) = 1)`.

### 3.23 Index bắt buộc
Đặt tên `idx_<bang>_<cot>`. Ngoài index tự sinh từ PK/UNIQUE:
- Mọi cột FK (vd. `idx_products_category_id`, `idx_orders_user_id`, `idx_order_items_order_id`…).
- `idx_categories_path` (`path varchar_pattern_ops`).
- `idx_orders_status_created_at (status, created_at)` — lọc đơn admin, job timeout.
- `idx_orders_completed_at` — thống kê doanh thu.
- `idx_products_is_active_created_at`, `idx_product_combos_is_active_created_at`.
- `idx_products_name_lower (lower(name))`, `idx_articles_title_lower (lower(title))` — tìm kiếm
  (`ILIKE`). Chuyển sang `pg_trgm` nếu chậm.
- `idx_notifications_user_id_is_read (user_id, is_read, created_at DESC)`.
- `idx_support_messages_user_id_created_at (user_id, created_at)`.
- `idx_discount_campaigns_active_dates (is_active, start_date, end_date)`.

## 4. Quy tắc nghiệp vụ bắt buộc

Mọi tham số có tên `app.*` là cấu hình trong `application.yml` (không hardcode — rule general.md).

**BR-1 — Tồn kho khả dụng.** `available = stock_quantity − reserved_quantity`, áp dụng cho cả
`products` và `product_combos`. Không cho thêm vào giỏ (tổng số lượng trong giỏ) hoặc tạo đơn vượt
`available`.

**BR-2 — Vòng đời giữ/trừ tồn kho** (áp cho từng dòng `order_items`, theo `product_id` hoặc
`combo_id`):
| Sự kiện | stock_quantity | reserved_quantity |
|---|---|---|
| Tạo đơn (→ PENDING) | — | + qty |
| PENDING → CONFIRMED | − qty | − qty |
| PENDING → CANCELLED / PAYMENT_FAILED | — | − qty |
| ReturnRequest → COMPLETED (đơn → RETURNED) | + qty (ghi `inventory_logs` reason=RETURN) | — |
| Điều chỉnh thủ công (FR-08) | ± change (ghi `inventory_logs`) | — |
Các bước sau CONFIRMED (SHIPPING, COMPLETED) không đổi tồn kho.

**BR-3 — Huỷ đơn quá hạn.** Job (`OrderTimeoutJob`, mỗi `app.order.timeout-job-delay` = 60s) tìm đơn `payment_method = VNPAY`,
`status = PENDING`, `created_at < now() − app.order.payment-timeout` (mặc định 15 phút); mỗi đơn xử
lý trong transaction riêng, khoá lại đơn và kiểm tra vẫn còn PENDING rồi mới huỷ (như huỷ thường,
`changed_by = NULL`, note "Hết hạn thanh toán"). URL VNPay đặt `vnp_ExpireDate` = thời điểm tạo +
cùng khoảng thời gian. Đơn COD không có timeout.

**BR-4 — Giá chốt khi tạo đơn** (D-04).
- Giỏ hàng không lưu giá, luôn hiển thị giá hiện hành.
- Khi tạo đơn, với mỗi dòng giỏ:
  - Sản phẩm: `original_price = products.price`, `unit_price = giá sau chiến dịch` (BR-6).
  - Combo: `original_price = unit_price = combo_price` (không áp chiến dịch).
  - `line_total = unit_price × quantity`; `subtotal = Σ line_total`.
- `discount_amount` theo BR-5 (0 nếu không có voucher).
- `shipping_fee = 0` nếu `subtotal − discount_amount ≥ app.shipping.free-threshold`, ngược lại phí GHN theo địa chỉ
  (D-27; hỏi GHN trước khi khoá tồn kho, không giữ khoá trong lúc gọi dịch vụ ngoài).
- `final_amount = subtotal − discount_amount + shipping_fee`.
- Nếu `final_amount ≠ expectedTotal` (client gửi) → `409 PRICE_CHANGED` kèm bảng giá mới, không tạo đơn.
- `order_items` không bao giờ đổi sau khi tạo.

**BR-5 — Voucher.**
- Hợp lệ khi: `is_active`, `start_date ≤ now() < expiry_date`, `used_count < quantity`,
  `subtotal ≥ min_order_value`, và user chưa có đơn nào dùng voucher này ở trạng thái khác
  `CANCELLED`/`PAYMENT_FAILED`.
- Mức giảm: PERCENTAGE → `subtotal × value / 100`, giới hạn bởi `max_discount` (nếu có);
  FIXED_AMOUNT → `value`. Luôn `≤ subtotal`. Làm tròn HALF_UP.
- Tính trên `subtotal` (đã gồm giảm của chiến dịch). Không áp lên phí vận chuyển.
- Khi tạo đơn: khoá voucher `SELECT … FOR UPDATE`, kiểm tra lại toàn bộ điều kiện, `used_count + 1`.
- Đơn → `CANCELLED`/`PAYMENT_FAILED`: `used_count − 1` (trong cùng transaction đổi trạng thái).
- Thứ tự lỗi: không tồn tại/tắt → `INVALID_VOUCHER`; ngoài thời hạn → `VOUCHER_EXPIRED`; chưa đạt
  tối thiểu → `VOUCHER_MIN_ORDER_NOT_MET`; hết lượt → `VOUCHER_EXHAUSTED`; đã dùng →
  `VOUCHER_ALREADY_USED`.
- `POST /vouchers/validate` chỉ kiểm tra/tính thử, không trừ lượt.

**BR-6 — Chiến dịch giảm giá.**
- Chiến dịch hiệu lực: `is_active` và `start_date ≤ now() < end_date`.
- Giá sau chiến dịch của sản phẩm = min trên các chiến dịch hiệu lực có chứa sản phẩm đó:
  PERCENTAGE → `price × (100 − value) / 100`; FIXED_AMOUNT → `max(price − value, 0)`. Không cộng
  dồn nhiều chiến dịch. Không có chiến dịch → bằng `price`.
- Cùng một hàm tính giá dùng cho: hiển thị (danh sách, chi tiết, giỏ) và tạo đơn
  (`PricingService` trong module `promotion`).

**BR-7 — Lịch sử trạng thái.** Mọi lần đổi `orders.status` (kể cả tạo đơn) ghi 1 dòng
`order_status_history` trong cùng transaction. `changed_by = NULL` khi do System.

**BR-8 — Nhật ký tồn kho.** Mọi thay đổi `stock_quantity` do điều chỉnh thủ công hoặc trả hàng
ghi 1 dòng `inventory_logs` (`stock_after` là giá trị sau khi đổi). Thay đổi do vòng đời đơn hàng
không ghi log (truy được qua `order_status_history`). Điều chỉnh không được làm
`stock_quantity < reserved_quantity` (`INVALID_STOCK_ADJUSTMENT`).

**BR-9 — Trả hàng** (D-07).
- Tạo yêu cầu khi: đơn của chính khách, `status = COMPLETED`,
  `now() ≤ completed_at + app.return.window-days` (mặc định 7), chưa có yêu cầu nào cho đơn.
- Trả nguyên đơn. Chuyển trạng thái yêu cầu theo mục 5.2; mỗi bước ghi `processed_by`,
  `processed_at`.
- `COMPLETED`: với từng dòng `stock_quantity += quantity` (BR-2) + log reason=RETURN; đơn
  `COMPLETED → RETURNED` (ghi history); payment giữ nguyên (hoàn tiền ngoài hệ thống).
- `REJECTED`: bắt buộc `note`; đơn giữ `COMPLETED`; khách không gửi lại được.

**BR-10 — Đánh giá** (D-09).
- Chỉ viết khi tồn tại đơn của user ở `status = COMPLETED` có dòng chứa sản phẩm/combo đó (đơn
  `RETURNED` không tính).
- Mỗi (user, sản phẩm) / (user, combo) tối đa 1 đánh giá. Chủ đánh giá sửa được (`rating`,
  `content`) và xoá cứng được; xoá xong được viết lại nếu vẫn đủ điều kiện.
- Điểm trung bình, số lượt tính bằng truy vấn (không lưu cột tổng hợp).

**BR-11 — OTP** (D-10).
- 6 chữ số ngẫu nhiên (`SecureRandom`), lưu BCrypt vào `otp_code_hash` cùng `otp_purpose`,
  `otp_expires_at = now() + app.otp.ttl` (10 phút), `otp_attempts = 0`, `otp_last_sent_at = now()`.
  OTP mới ghi đè OTP cũ.
- Gửi lại khi `now() − otp_last_sent_at < app.otp.resend-interval` (60 giây) →
  `OTP_RESEND_TOO_SOON`.
- Kiểm tra: purpose khác hoặc không có OTP → `INVALID_OTP`; hết hạn → `OTP_EXPIRED`;
  `otp_attempts ≥ app.otp.max-attempts` (5) → `OTP_TOO_MANY_ATTEMPTS`; sai → `otp_attempts + 1`,
  `INVALID_OTP`; đúng → xoá các cột OTP.
- Chưa `email_verified` → không đăng nhập được (`EMAIL_NOT_VERIFIED`).

**BR-12 — Kết quả thanh toán VNPay (IPN + Return URL, D-28).**
- Hàm dùng chung `handlePaymentResult` cho `GET /api/vnpay/ipn` và `GET /api/vnpay/return`; endpoint public; xác minh
  `vnp_SecureHash` (HMAC-SHA512 bằng `VNP_HASH_SECRET`) trước mọi thứ.
- Tìm đơn theo `vnp_TxnRef = order_code`; `vnp_Amount / 100` phải bằng `final_amount`.
- Khoá dòng `orders` (`FOR UPDATE`). Nếu `payments.status ≠ PENDING` → đã xử lý, trả lại mã
  "đã xác nhận" cho VNPay, không làm gì (idempotent). `transaction_id` unique là lớp bảo vệ thứ hai.
- `vnp_ResponseCode = 00` và `vnp_TransactionStatus = 00` → payment `SUCCESS` (`transaction_id`,
  `payload`, `paid_at`), đơn `PENDING → CONFIRMED` (BR-2). Ngược lại → payment `FAILED`, đơn
  `PAYMENT_FAILED` (giải phóng giữ chỗ, trả lượt voucher).
- Thanh toán thành công nhưng đơn không còn PENDING (đã bị huỷ do timeout) → payment `SUCCESS`, đơn
  giữ nguyên, ghi log WARN để hoàn tiền thủ công (xem `known-issues.md`).
- IPN trả đúng định dạng VNPay (`RspCode`, `Message`): 00 đã ghi nhận, 01 không có đơn, 02 đã xác nhận trước đó,
  04 sai số tiền, 97 sai chữ ký, 99 lỗi khác.
- Return URL dùng cùng hàm (đổi trạng thái được — D-28, vì localhost không nhận IPN); gọi lặp/đồng thời với IPN an toàn nhờ
  khoá đơn + kiểm `payments.status`. Sau khi CONFIRMED, vận đơn GHN được tạo bất đồng bộ sau commit.

**BR-13 — Danh mục.**
- `path = parent.path + id + '/'` (gốc: `'/' + id + '/'`), gán sau khi insert có id.
- Đổi cha: chặn nếu cha mới là chính nó hoặc nằm trong cây con (`new_parent.path LIKE old_path || '%'`
  → `CIRCULAR_CATEGORY_DEPENDENCY`); cập nhật `path` cho cả cây con bằng một câu `UPDATE … SET path =
  new_prefix || substring(path from length(old_prefix) + 1) WHERE path LIKE old_prefix || '%'`.
- Xoá: chặn nếu còn danh mục con, sản phẩm hoặc combo → `CATEGORY_NOT_EMPTY`.

**BR-14 — Mặt hàng ngừng kinh doanh.** `is_active = false`: ẩn khỏi trang public, tìm kiếm, gợi ý;
không thêm vào giỏ/đặt hàng được (`PRODUCT_INACTIVE`); vẫn hiển thị trong giỏ (cảnh báo) và trong đơn
cũ (nhờ snapshot). Không xoá cứng sản phẩm/combo đã từng nằm trong đơn.

## 5. Máy trạng thái

### 5.1 Đơn hàng (`orders.status`)
```
PENDING ──► CONFIRMED ──► SHIPPING ──► COMPLETED ──► RETURNED
   ├──► CANCELLED
   └──► PAYMENT_FAILED
```
| Từ | Đến | Ai | Điều kiện / hiệu ứng |
|---|---|---|---|
| (mới) | PENDING | Customer | Tạo đơn thành công: giữ chỗ tồn kho, trừ lượt voucher, payment PENDING |
| PENDING | CONFIRMED | System | IPN VNPay thành công (BR-12) → trừ tồn kho |
| PENDING | CONFIRMED | Employee, Admin | Chỉ đơn **COD** → trừ tồn kho |
| PENDING | CANCELLED | Customer | Chủ đơn tự huỷ → giải phóng giữ chỗ, trả lượt voucher |
| PENDING | CANCELLED | Employee, Admin | Huỷ đơn **COD** (vd. không liên lạc được khách); bắt buộc note |
| PENDING | CANCELLED | System | Đơn VNPay quá hạn thanh toán (BR-3) |
| PENDING | PAYMENT_FAILED | System | IPN báo thất bại → giải phóng giữ chỗ, trả lượt voucher |
| CONFIRMED | SHIPPING | Employee, Admin | Đã bàn giao vận chuyển (note: mã vận đơn) |
| SHIPPING | COMPLETED | Employee, Admin | Khách đã nhận hàng → gán `completed_at`; COD: payment `SUCCESS`, `paid_at` |
| COMPLETED | RETURNED | System (do nhân viên hoàn tất trả hàng) | Chỉ qua ReturnRequest → COMPLETED (BR-9) |

Mọi chuyển khác → `INVALID_STATUS_TRANSITION`. Trạng thái cuối: CANCELLED, PAYMENT_FAILED, RETURNED
(và COMPLETED nếu không có trả hàng). Định nghĩa bảng chuyển hợp lệ trong enum `OrderStatus`
(`canTransitionTo`), không rải `if` trong service.

### 5.2 Yêu cầu trả hàng (`return_requests.status`)
```
PENDING ──► APPROVED ──► COMPLETED
   └──► REJECTED
```
| Từ | Đến | Ai | Hiệu ứng |
|---|---|---|---|
| (mới) | PENDING | Customer | BR-9; thông báo cho nhân viên (RETURN_NEW) |
| PENDING | APPROVED | Employee, Admin | Thông báo + email cho khách |
| PENDING | REJECTED | Employee, Admin | Bắt buộc note; thông báo + email |
| APPROVED | COMPLETED | Employee, Admin | Cộng tồn kho + log, đơn → RETURNED; thông báo + email |

### 5.3 Thanh toán (`payments.status`)
`PENDING → SUCCESS` (IPN thành công hoặc đơn COD COMPLETED) · `PENDING → FAILED` (IPN thất bại).
Đơn huỷ khi payment còn PENDING → payment giữ PENDING (không có giao dịch).

## 6. Khoá & chống bán vượt tồn kho

**Thứ tự khoá toàn hệ thống** (mọi transaction phải khoá theo đúng thứ tự này để tránh deadlock):
`orders` → `products` (id tăng dần) → `product_combos` (id tăng dần) → `vouchers`.
Khoá bằng `@Lock(LockModeType.PESSIMISTIC_WRITE)` trên repository (`SELECT … FOR UPDATE`).

**Tạo đơn** (một `@Transactional`, isolation mặc định READ COMMITTED):
```java
@Transactional
public OrderResponse createOrder(Long userId, CreateOrderRequest req) {
    Cart cart = cartRepo.findByUserId(userId);              // giỏ của chính user
    if (cart.getItems().isEmpty()) throw new BusinessException(CART_EMPTY);

    // 1. Khoá sản phẩm theo id tăng dần, rồi combo theo id tăng dần
    List<Long> productIds = cart.productIdsSorted();
    List<Long> comboIds   = cart.comboIdsSorted();
    Map<Long, Product> products = productRepo.findAllByIdInForUpdateOrderById(productIds);
    Map<Long, ProductCombo> combos = comboRepo.findAllByIdInForUpdateOrderById(comboIds);

    // 2. Kiểm tra is_active + available (BR-1, BR-14), tính giá (BR-4, BR-6)
    // 3. Voucher (nếu có): khoá FOR UPDATE, kiểm tra BR-5, tính discount, used_count + 1
    // 4. Tính shipping_fee, final_amount; so với req.expectedTotal → PRICE_CHANGED
    // 5. reserved_quantity += qty cho từng dòng
    // 6. Tạo orders, order_items (snapshot), payments(PENDING), order_status_history
    // 7. Xoá các cart_items đã đặt; nếu saveAsDefault → cập nhật users.phone/address
    // 8. VNPAY → paymentGateway.createPaymentUrl(order) (lỗi → PAYMENT_GATEWAY_ERROR, rollback)
    // 9. Publish OrderCreatedEvent (listener AFTER_COMMIT gửi email/thông báo)
}
```
Mọi exception nghiệp vụ trong transaction → rollback toàn bộ (không có đơn "nửa vời").

**Đổi trạng thái** (IPN, huỷ, xác nhận, trả hàng): khoá `orders` trước, kiểm tra trạng thái hiện tại
sau khi khoá, rồi khoá products/combos/voucher theo thứ tự trên nếu cần đổi tồn kho/lượt.

**Điều chỉnh tồn kho:** khoá dòng `products`/`product_combos` rồi cập nhật + ghi log.

**Test bắt buộc** (Testcontainers, PostgreSQL thật — rule testing.md): N request đặt song song cùng
1 sản phẩm còn ít hàng; tương tự với combo; voucher còn 1 lượt; IPN gọi lặp.

## 7. Dữ liệu seed (thư mục `db/seed`, chỉ profile dev)
- Migration cấu trúc nằm ở `backend/src/main/resources/db/migration`; dữ liệu mẫu nằm riêng ở
  `db/seed` và chỉ chạy khi `spring.flyway.locations` có `classpath:db/seed`
  (`application-dev.yml.example`). Test và production KHÔNG chạy seed. Số phiên bản giữa hai thư mục
  dùng chung một dãy (không trùng số).
- Hiện có `db/seed/V3__seed_catalog.sql` (và `V4__seed_combos.sql`: 5 combo mẫu): 271 danh mục (3 cấp), 400 sản phẩm, 701 ảnh trên Cloudinary,
  50 thương hiệu, tồn kho ngẫu nhiên (40 sản phẩm = 0 để thử "Hết hàng"). Nguồn: dữ liệu mock frontend
  (cửa hàng tham khảo — tên "WeTrek"/hotline đã đổi). Nhóm cần cân nhắc bản quyền trước khi dùng cho
  demo/bảo vệ.
- Sinh lại: `pg_dump --data-only --column-inserts --rows-per-insert=50 -t categories -t products -t images`,
  chỉ giữ các lệnh INSERT + `setval` (bỏ phần `SET`/`set_config` đầu file).
- Còn thiếu (thêm khi có module): 1 Admin, 1 Employee, 1 Customer (mật khẩu ghi trong
  `local-development.md`), combo, voucher, chiến dịch, bài viết.
