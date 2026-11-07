# Plan: Khuyến mãi — Voucher + Chiến dịch giảm giá

- **Ngày tạo:** 2026-10-31
- **Người/agent phụ trách:** Người 4 (Claude Code hỗ trợ), nhánh `feature/promotion` (tạo từ `origin/feature/order-payment-service`)
- **Tier:** 2 (FR-16, FR-17; BR-5, BR-6)
- **Module backend liên quan:** `promotion` (chính), `order` (trừ/trả lượt voucher), `catalog` + `combo` (giá sau chiến dịch),
  `cart` (đã dùng `PricingService`), `auth` (`SecurityConfig`), `common` (`ErrorCode`)
- **Module/feature frontend liên quan:** `features/cart` (popup voucher), `features/layout/PromoBar`, `features/catalog`,
  `features/product`, `/promotions/[slug]`, admin `/admin/vouchers`, `/admin/campaigns` (+ `new`, `[id]`)

## Mục tiêu
- **Admin** tạo/sửa/tắt/xoá voucher và chiến dịch giảm giá, gán sản phẩm vào chiến dịch.
- **Khách** áp voucher ở `/cart` bằng **nhập mã hoặc chọn từ danh sách mã công khai**; thấy mã đang có trên PromoBar.
- **Giá chiến dịch** hiển thị thống nhất ở mọi nơi (thẻ, chi tiết, giỏ, đặt hàng, lọc/sắp xếp theo giá) và trang
  `/promotions/[slug]` đọc dữ liệu thật.

## Phạm vi
- **Trong phạm vi:**
  1. Schema `vouchers` (+ cột mới `is_public`), `discount_campaigns`, `discount_campaign_products`, FK `orders.voucher_id`; seed dev.
  2. Voucher: kiểm tra BR-5, trừ lượt khi tạo đơn (khoá `FOR UPDATE`), trả lượt khi CANCELLED/PAYMENT_FAILED; API admin CRUD;
     `POST /vouchers/validate`; **mới** `GET /vouchers/available` (danh sách cho popup) và `GET /vouchers/public` (PromoBar).
  3. Chiến dịch: `PricingService` thật (BR-6), giá sau giảm trong mọi `ItemSummary`/`ProductDetail`, lọc + sắp xếp theo giá sau
     giảm, `GET /campaigns/{slug}`, API admin CRUD + kiểm tra chồng chiến dịch (cảnh báo, không chặn).
  4. Frontend admin 2 trang, popup voucher có danh sách, PromoBar dữ liệu thật, hiển thị giá thống nhất, trang chiến dịch thật.
  5. **(Tuỳ chọn, làm cuối)** Email chào mừng kèm mã ẩn sau khi xác thực email — chỉ làm khi đã rebase lên `dev` (có module mail).
- **Ngoài phạm vi (cố tình không làm):**
  - Ví voucher cá nhân / bảng `user_vouchers`, voucher tặng theo sự kiện (hoàn tất đơn, sinh nhật), admin tặng mã cho từng khách
    → ghi vào "hướng phát triển" của báo cáo.
  - Voucher theo danh mục/sản phẩm, voucher miễn phí ship, flash sale giới hạn số lượng, cộng dồn nhiều voucher.
  - Chiến dịch áp cho combo (BR-4: combo luôn bán theo `combo_price`).

## Quyết định thiết kế (đã chốt với người dùng 2026-10-31)
| # | Vấn đề | Chọn | Ghi vào |
|---|---|---|---|
| 1 | Cách khách lấy mã | Nhập mã + danh sách mã **công khai** (`is_public`); mã ẩn chỉ nhập tay | D-29 |
| 2 | Giá chiến dịch cho lọc/sắp xếp | Tính lúc truy vấn bằng **một biểu thức SQL duy nhất** (`@Formula` `Product.salePrice`); `PricingService` đọc giá trị đó | D-30 |
| 3 | "Khách đã dùng mã chưa" | `EXISTS` trên `orders` (khác CANCELLED/PAYMENT_FAILED), không thêm bảng | BR-5 (giữ nguyên) |
| 4 | Chống vượt lượt khi đặt đồng thời | `SELECT … FOR UPDATE` dòng voucher, sau khi khoá products/combos; kiểm tra lại BR-5 **sau khi** có khoá | database.md mục 6 (giữ nguyên) |
| 5 | Chiến dịch chồng nhau | Cho phép, lấy mức giảm lớn nhất; admin thấy cảnh báo | BR-6 (giữ nguyên) |
| 6 | Mã voucher trùng | Mã lỗi mới `VOUCHER_CODE_ALREADY_EXISTS` (409), cùng kiểu với `SLUG_ALREADY_EXISTS` | api.md mục 2 |

**Lý do chọn #2:** `GET /products` đang lọc/sắp xếp bằng JPA `Specification` + `Sort` trên cột `price`. `@Formula` biến giá sau giảm
thành một thuộc tính `salePrice` dùng được ngay trong `Specification` và `Sort`, không phải viết lại query thành native. Vì chỉ có
**một** công thức (SQL) nên giá ở danh sách, chi tiết, giỏ và lúc đặt hàng không thể lệch nhau. Đánh đổi: entity `catalog.Product`
chứa SQL tham chiếu bảng của module `promotion`; giờ hiệu lực lấy theo `now()` của PostgreSQL (test thời gian phải đặt
`start_date`/`end_date` tương đối với giờ DB, không dùng `Clock` giả).

## Thiết kế API (cập nhật `api.md` TRƯỚC khi code — bước 0)

### Khách hàng
| Method | Path | Quyền | Body → Response |
|---|---|---|---|
| POST | `/vouchers/validate` | Customer | (đã có trong docs) `{code, cartItemIds?}` → `{code, discountType, discountValue, discountAmount, ...OrderPreview}`. Gọi lại `OrderService.preview` |
| GET | `/vouchers/available` | Customer | **mới** `?cartItemIds=1,2` (bỏ trống = cả giỏ) → `[AvailableVoucher]`: mã công khai đang hiệu lực, còn lượt; xếp mã dùng được trước, mã giảm nhiều nhất có `best: true` |
| GET | `/vouchers/public` | Public | **mới** → `[{code, discountType, discountValue, maxDiscount, minOrderValue, expiryDate}]` (tối đa 5, sắp hết hạn trước) — PromoBar |
| GET | `/campaigns/{slug}` | Public | (đã có) chỉ chiến dịch đang hiệu lực, ngược lại 404 |

```ts
interface AvailableVoucher {
  code: string; discountType: DiscountType; discountValue: Money; maxDiscount: Money | null;
  minOrderValue: Money; expiryDate: string; remaining: number;      // quantity − used_count
  usable: boolean;
  reason: "VOUCHER_MIN_ORDER_NOT_MET" | "VOUCHER_ALREADY_USED" | null;
  missingAmount: Money | null;                                      // còn thiếu bao nhiêu để đạt đơn tối thiểu
  estimatedDiscount: Money;                                          // 0 khi !usable
  best: boolean;
}
```

### Admin (chỉ ADMIN — `@PreAuthorize("hasRole('ADMIN')")`)
| Method | Path | Ghi chú |
|---|---|---|
| GET | `/admin/vouchers` | `?q&status=UPCOMING\|ACTIVE\|EXPIRED\|EXHAUSTED\|INACTIVE&page&limit` → `[AdminVoucher]` (kèm `status` tính sẵn) |
| POST / PUT | `/admin/vouchers`, `/admin/vouchers/{id}` | `{code, discountType, discountValue, maxDiscount?, minOrderValue, quantity, startDate, expiryDate, isActive, isPublic}` |
| GET / DELETE | `/admin/vouchers/{id}` | DELETE đã có đơn dùng → `VOUCHER_IN_USE` |
| GET | `/admin/campaigns` | `?q&status=UPCOMING\|RUNNING\|ENDED\|INACTIVE&page&limit` → kèm `productCount` |
| POST / PUT | `/admin/campaigns`, `/admin/campaigns/{id}` | (đã có trong docs) `{name, slug?, description?, bannerUrl?, discountType, discountValue, startDate, endDate, isActive, productIds}` |
| GET / DELETE | `/admin/campaigns/{id}` | GET trả kèm danh sách sản phẩm (giá gốc, giá sau giảm của chiến dịch này) |
| GET | `/admin/campaigns/overlaps` | **mới** `?productIds&startDate&endDate&excludeId` → `[{productId, campaignId, campaignName}]` — chỉ để cảnh báo |

Kiểm tra dữ liệu (`VALIDATION_ERROR` + `fieldErrors`): PERCENTAGE 1–100, FIXED_AMOUNT > 0; `maxDiscount` chỉ cho PERCENTAGE;
`expiryDate > startDate` / `endDate > startDate`; `quantity ≥ used_count` khi sửa; mã voucher `[A-Z0-9_-]{3,50}` (lưu IN HOA).

## Migration dự kiến

`backend/src/main/resources/db/migration/V6__promotion.sql` (V5 là `V5__order_schema.sql` của nhánh order):
```sql
CREATE TABLE vouchers (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            VARCHAR(50)    NOT NULL UNIQUE,
    discount_type   VARCHAR(30)    NOT NULL CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    discount_value  NUMERIC(19, 0) NOT NULL,
    max_discount    NUMERIC(19, 0),
    min_order_value NUMERIC(19, 0) NOT NULL DEFAULT 0 CHECK (min_order_value >= 0),
    quantity        INT            NOT NULL CHECK (quantity > 0),
    used_count      INT            NOT NULL DEFAULT 0,
    start_date      TIMESTAMPTZ    NOT NULL,
    expiry_date     TIMESTAMPTZ    NOT NULL,
    is_active       BOOLEAN        NOT NULL DEFAULT TRUE,
    is_public       BOOLEAN        NOT NULL DEFAULT TRUE,   -- D-29
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (code = upper(code)),
    CHECK (expiry_date > start_date),
    CHECK (used_count >= 0 AND used_count <= quantity),
    CHECK ((discount_type = 'PERCENTAGE' AND discount_value BETWEEN 1 AND 100)
        OR (discount_type = 'FIXED_AMOUNT' AND discount_value > 0 AND max_discount IS NULL)),
    CHECK (max_discount IS NULL OR max_discount > 0)
);

CREATE TABLE discount_campaigns (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name           VARCHAR(255)   NOT NULL,
    slug           VARCHAR(280)   NOT NULL UNIQUE,
    description    TEXT,
    banner_url     VARCHAR(500),
    discount_type  VARCHAR(30)    NOT NULL CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    discount_value NUMERIC(19, 0) NOT NULL,
    start_date     TIMESTAMPTZ    NOT NULL,
    end_date       TIMESTAMPTZ    NOT NULL,
    is_active      BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (end_date > start_date),
    CHECK ((discount_type = 'PERCENTAGE' AND discount_value BETWEEN 1 AND 100)
        OR (discount_type = 'FIXED_AMOUNT' AND discount_value > 0))
);
CREATE INDEX idx_discount_campaigns_active_dates ON discount_campaigns (is_active, start_date, end_date);

CREATE TABLE discount_campaign_products (
    campaign_id BIGINT NOT NULL REFERENCES discount_campaigns (id) ON DELETE CASCADE,
    product_id  BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    PRIMARY KEY (campaign_id, product_id)
);
CREATE INDEX idx_discount_campaign_products_product_id ON discount_campaign_products (product_id);

-- Tên FK theo gợi ý trong V5; V5 đã có idx_orders_voucher_id (đủ cho kiểm tra "đã dùng mã" của BR-5)
ALTER TABLE orders ADD CONSTRAINT fk_orders_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers (id);
```

`backend/src/main/resources/db/seed/V7__seed_promotions.sql` (chỉ profile dev): `CAMPVIVO10` (10%, tối đa 50.000đ, đơn từ 500.000đ,
công khai), `GIAM50K` (50.000đ, đơn từ 1.500.000đ, công khai), `WELCOME50K` (ẩn), 1 chiến dịch đang chạy (~8 sản phẩm của seed V3,
giảm 20%, hết hạn sau 30 ngày tính từ lúc chạy seed) + 1 chiến dịch sắp diễn ra.

**Biểu thức giá sau chiến dịch** (`Product.salePrice`, `@Formula`, BR-6, làm tròn HALF_UP — `ROUND` của PostgreSQL với số dương):
```sql
(SELECT COALESCE(MIN(CASE WHEN c.discount_type = 'PERCENTAGE'
                          THEN ROUND(price * (100 - c.discount_value) / 100.0)
                          ELSE GREATEST(price - c.discount_value, 0) END), price)
   FROM discount_campaign_products cp JOIN discount_campaigns c ON c.id = cp.campaign_id
  WHERE cp.product_id = id AND c.is_active AND c.start_date <= now() AND c.end_date > now())
```

## File sẽ tạo/sửa

### Backend
| File | Tạo/Sửa | Ghi chú |
|---|---|---|
| `db/migration/V6__promotion.sql` | Tạo | Như trên |
| `db/seed/V7__seed_promotions.sql` | Tạo | Dữ liệu demo |
| `promotion/DiscountType.java` | Tạo | `PERCENTAGE`, `FIXED_AMOUNT` |
| `promotion/Voucher.java`, `VoucherRepository.java` | Tạo | `findByCode`, `findByCodeForUpdate` (`PESSIMISTIC_WRITE`), `existsActiveUsage(voucherId, userId)` (native `EXISTS` trên `orders`), `hasOrders(voucherId)` |
| `promotion/VoucherRules.java` | Tạo | Thuần Java, không DB: kiểm tra theo **đúng thứ tự lỗi BR-5**, tính mức giảm (HALF_UP, trần `max_discount`, ≤ subtotal) |
| `promotion/VoucherService.java` | Sửa | `quote(code, subtotal, userId)` (chỉ đọc), `consume(...)` (khoá + kiểm tra lại + `used_count + 1`, trả `VoucherQuote{voucherId, code, discount}`), `release(voucherId)` (`used_count − 1`), `available(userId, subtotal)`, `publicList()` |
| `promotion/VoucherController.java` | Tạo | `/api/vouchers/validate`, `/available`, `/public` |
| `promotion/DiscountCampaign.java`, `DiscountCampaignRepository.java` | Tạo | `findActiveBySlug`, `findActiveForProducts(ids)` (lấy tên/slug/hạn chiến dịch tốt nhất), `findOverlaps(...)` |
| `promotion/PricingService.java` | Sửa | `unitPrice(Product)` → `product.getSalePrice()`; thêm `activeCampaigns(productIds)` cho trường `campaign` của chi tiết sản phẩm |
| `promotion/CampaignService.java`, `CampaignController.java` | Tạo | `GET /api/campaigns/{slug}` |
| `promotion/admin/AdminVoucherController.java`, `AdminVoucherService.java`, `VoucherRequest.java`, `AdminVoucherResponses.java` | Tạo | Theo khuôn `combo/admin/*` |
| `promotion/admin/AdminCampaignController.java`, `AdminCampaignService.java`, `CampaignRequest.java`, `AdminCampaignResponses.java` | Tạo | Slug tự sinh bằng `SlugUtil`, trùng → `SLUG_ALREADY_EXISTS` |
| `catalog/Product.java` | Sửa | Thêm `@Formula salePrice` (chỉ đọc) |
| `catalog/dto/ItemSummary.java` | Sửa | `price = p.getSalePrice()`; sửa javadoc |
| `catalog/dto/ProductDetailResponse.java`, `catalog/ProductService.java` | Sửa | `price` sau giảm; trường `campaign` lấy từ `PricingService`; `minPrice`/`maxPrice` lọc theo `salePrice` |
| `catalog/ProductQuery.java` | Sửa | `price_asc`/`price_desc` sắp theo `salePrice` |
| `combo/ComboService.java`, `combo/dto/*` | Kiểm tra | Món trong combo (`ItemSummary`) tự đúng; `itemsTotal` (tổng giá lẻ **hiện tại**) đổi sang `salePrice` |
| `order/Order.java` | Sửa | Constructor nhận thêm `voucherId` |
| `order/OrderService.java` | Sửa | `preview` + ước tính phí ship dùng `quote`; `create` dùng `consume` **sau khi khoá products/combos**, ghi `voucherId`; thêm `checkoutSubtotal(userId, cartItemIds)` cho `/vouchers/available` |
| `order/OrderStateService.java` | Sửa | CANCELLED/PAYMENT_FAILED → `voucherService.release(order.getVoucherId())` khi khác null (thay comment dòng 69) |
| `auth/SecurityConfig.java` | Sửa | `permitAll` GET `/api/vouchers/public` (`/api/campaigns/**` đã có) |
| `common/exception/ErrorCode.java` (+ `ErrorCodeTest`) | Sửa | `VOUCHER_CODE_ALREADY_EXISTS` |

### Frontend
| File | Tạo/Sửa | Ghi chú |
|---|---|---|
| `types/api/promotion.ts` | Tạo | `DiscountType`, `AvailableVoucher`, `PublicVoucher`, `CampaignDetail`, kiểu admin |
| `services/promotionService.ts` | Tạo | `getAvailableVouchers`, `getPublicVouchers`, `getCampaign` |
| `services/admin/adminVoucherService.ts`, `adminCampaignService.ts` | Tạo | Theo khuôn `adminComboService.ts` |
| `lib/errorMessages.ts` | Sửa | `VOUCHER_CODE_ALREADY_EXISTS` |
| `components/features/cart/VoucherList.tsx` | Tạo | Danh sách trong popup: thẻ mã, nút Áp dụng, mã mờ + lý do ("Mua thêm X đ"), nhãn "Tốt nhất" |
| `components/features/cart/CartPage.tsx` | Sửa | Popup gắn `VoucherList`; tải lại danh sách khi giỏ/dòng được chọn đổi |
| `components/features/layout/Shell.tsx`, `PromoBar.tsx` | Sửa | Slide từ `GET /vouchers/public` (lỗi/rỗng → slide mock hiện có) |
| ~~`components/ui/PriceTag.tsx`~~ | Không làm | **Đổi khi làm (2026-10-31):** `catalogService.toCard`/`toProductFull` đã sẵn giá gạch + badge % từ `originalPrice`/`price` → chỉ cần backend trả giá sau giảm |
| `features/product/ProductInfo.tsx`, `types/product.ts`, `catalogService.ts` | Sửa | Nhãn chiến dịch (`ctkm-campaign-tag` có sẵn) + link `/promotions/[slug]`; đếm ngược hiện cả số ngày, bỏ "cuộn ngày" của dữ liệu mock |
| `app/(store)/(shop)/promotions/[slug]/page.tsx`, `services/catalogService.ts` | Sửa | `getPromo` → `GET /campaigns/{slug}`, 404 khi API trả 404 |
| `app/(admin)/admin/vouchers/page.tsx`, `components/admin/promotion/VoucherListView.tsx`, `VoucherFormModal.tsx` | Tạo | Bảng + lọc + modal; lỗi `VOUCHER_IN_USE` → gợi ý nút Tắt |
| `app/(admin)/admin/campaigns/page.tsx`, `new/page.tsx`, `[id]/page.tsx`, `components/admin/promotion/CampaignListView.tsx`, `CampaignForm.tsx`, `CampaignProductPicker.tsx` | Tạo | Tái dùng `ImageListField`/upload (`folder=campaigns`), `RichTextEditor`, `PageHeader` |

## Thứ tự thực hiện (xong mỗi phần dừng lại cho người dùng test)

0. **Contract + docs trước khi code:** `api.md` (3 endpoint mới, `AvailableVoucher`, mã lỗi mới, bỏ dòng "chưa có module promotion"
   ở mục 4.5), `database.md` (cột `is_public`, số migration V6, index mới, seed V7), `decisions.md` D-29 + D-30, `features.md`
   FR-16 (`isPublic`, danh sách mã ở checkout), `pages.md`.
1. **Phần 1 — Schema:** `V6__promotion.sql`, entity + repository, `V7__seed_promotions.sql`. Khởi động backend với
   `ddl-auto=validate` để chắc entity khớp bảng.
2. **Phần 2 — Voucher backend:** `VoucherRules` + unit test → `VoucherService` → nối `OrderService`/`OrderStateService` →
   `VoucherController` → admin voucher API. Gỡ KI-08.
3. **Phần 3 — Voucher frontend:** `/admin/vouchers`, `VoucherList` trong popup `/cart`, PromoBar.
   ➜ *Điểm dừng 1: có thể mở PR "voucher" tại đây.*
4. **Phần 4 — Chiến dịch backend:** `Product.salePrice` → `PricingService` → `ItemSummary`/`ProductDetail`/combo → lọc + sắp xếp →
   `GET /campaigns/{slug}` → admin campaign API + `overlaps`.
5. **Phần 5 — Chiến dịch frontend:** `PriceTag` ở thẻ/chi tiết, `/promotions/[slug]` thật, `/admin/campaigns` (+ new, [id]).
   ➜ *Điểm dừng 2: PR "chiến dịch".*
6. **Phần 6 (tuỳ chọn) — Email chào mừng:** rebase lên `dev` (đã có đăng ký + gửi mail), cấu hình `app.promotion.welcome-voucher-code`
   (rỗng = tắt), gửi sau khi xác thực email thành công; không chặn xác thực nếu gửi lỗi.
7. **Hoàn tất:** `./scripts/agent-lint.sh`, `./scripts/agent-test.sh`, cập nhật `progress.md`, `known-issues.md` (đóng KI-08),
   chuyển plan sang `completed/`.

## Ảnh hưởng tới hệ thống hiện có
- **Luồng đặt hàng (Người 1):** `create` thêm bước khoá voucher — thứ tự khoá `orders → products → product_combos → vouchers`
  không đổi (database.md mục 6). Huỷ/thanh toán lỗi trả lượt trong cùng transaction đổi trạng thái.
- **Giá:** mọi nơi dùng `ItemSummary`/`PricingService` đổi sang giá sau giảm — giỏ hàng có thể báo `PRICE_CHANGED` khi chiến dịch
  bắt đầu/kết thúc giữa chừng (đúng D-04). `order_items` là snapshot nên đơn cũ không đổi.
- **Catalog (Người 2):** `Product` có thêm thuộc tính `@Formula` (mỗi lần đọc sản phẩm chạy thêm 1 subquery có index). Admin sản phẩm
  vẫn hiển thị giá gốc `price`.
- **Phụ thuộc nhánh:** V6 cần V5 của `feature/order-payment-service`. Nhánh order phải merge vào `dev` trước PR này; trước khi merge
  kiểm lại trên `dev` không ai dùng số V6/V7.
- **DB local:** chỉ thêm bảng, không cần reset DB.

## Cách test
- **Unit (`VoucherRulesTest`, bảng ca):** không mã; PERCENTAGE có/không trần; FIXED > subtotal → = subtotal; làm tròn HALF_UP;
  đúng thứ tự lỗi BR-5 khi vi phạm nhiều điều kiện cùng lúc; biên `start_date` (= now hợp lệ) và `expiry_date` (= now hết hạn).
- **Integration (Testcontainers, PostgreSQL thật):**
  - Voucher: đặt COD có mã → `used_count + 1`, `orders.voucher_id` đúng; huỷ PENDING / IPN thất bại / job hết hạn → trả lượt;
    dùng lần 2 → `VOUCHER_ALREADY_USED`; dùng lại sau khi đơn trước bị huỷ → được; hết lượt → `VOUCHER_EXHAUSTED`;
    **20 luồng đặt đồng thời, mã còn 1 lượt → đúng 1 đơn thành công**; 1 khách 2 đơn song song cùng mã → 1 đơn.
  - Admin voucher: xoá mã đã dùng → 409 `VOUCHER_IN_USE`; sửa `quantity < used_count` → 400; mã trùng → 409; EMPLOYEE → 403.
  - `/vouchers/available`: mã ẩn không xuất hiện; `missingAmount` đúng; `best` đúng mã giảm nhiều nhất.
  - Chiến dịch: không chiến dịch / 2 chiến dịch chồng (lấy mức thấp nhất) / hết hạn / chưa bắt đầu / tắt / FIXED > giá → 0;
    `GET /products` có `originalPrice` + `price` đúng; sản phẩm 500k giảm 20% lọt khoảng 300–450k; `price_asc` theo giá sau giảm;
    đặt hàng dùng giá sau giảm và `order_items.original_price` = giá gốc; combo không đổi giá; `/campaigns/{slug}` hết hạn → 404;
    `endDate ≤ startDate` → 400.
- **Thử tay (người dùng):** tạo voucher ở admin → thấy trong popup và PromoBar → áp mã → đặt COD → huỷ → lượt trả lại; tạo chiến
  dịch → giá thẻ/chi tiết/giỏ đổi ngay → `/promotions/[slug]` hiển thị đúng; mobile 375px cho popup voucher.

## Danh sách task

**Quy ước làm việc (người dùng chốt 2026-10-31):** mọi task làm và commit trên nhánh `feature/promotion`. KHÔNG merge, rebase,
push lên hay sửa nhánh khác (`dev`, `master`, `feature/order-payment-service`...). Phần 6 cần rebase lên `dev` nên **chờ người
dùng cho phép** mới làm. Mỗi task xong: tick ô. **Chưa `git add`/commit/push** cho tới khi người dùng yêu cầu (chốt 2026-10-31).

Cột "Roadmap" ánh xạ sang task gốc trong `backend-roadmap.md` (tick bên đó khi xong cả nhóm task tương ứng).

### Bước 0 — Contract + docs

| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] PM-01 | Docs | `api.md`: thêm `GET /vouchers/available`, `GET /vouchers/public`, `GET /admin/campaigns/overlaps`, kiểu `AvailableVoucher`, trường `isPublic` ở body admin voucher, query `status` của 2 danh sách admin; mã lỗi `VOUCHER_CODE_ALREADY_EXISTS`; bỏ dòng "Chưa có module promotion..." mục 4.5 | — | Mọi endpoint/kiểu ở mục "Thiết kế API" có trong `api.md` |
| - [x] PM-02 | Docs | `database.md`: cột `is_public` ở 3.14, index `idx_discount_campaign_products_product_id` ở 3.23, seed khuyến mãi ở mục 7; `decisions.md`: D-29 (mã công khai + danh sách, không ví cá nhân), D-30 (`@Formula salePrice`, giờ theo `now()` của DB); `features.md` FR-16; `pages.md` (`/cart` có danh sách mã, PromoBar) | — | Docs khớp plan; không còn chỗ nói "voucher luôn INVALID_VOUCHER" |

### Phần 1 — Schema + seed (Roadmap F-07)

| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] PM-03 | BE | `V6__promotion.sql` (3 bảng, CHECK, index, FK `fk_orders_voucher`) | PM-02 | Backend khởi động sạch, Flyway báo V6 thành công |
| - [x] PM-04 | BE | Entity + repository: `DiscountType`, `Voucher`, `VoucherRepository`, `DiscountCampaign` (quan hệ `@ManyToMany` với `Product` qua `discount_campaign_products`), `DiscountCampaignRepository`; `ErrorCode.VOUCHER_CODE_ALREADY_EXISTS` + `ErrorCodeTest` | PM-03 | Chạy với `ddl-auto=validate` không lỗi; `ErrorCodeTest` xanh |
| - [x] PM-05 | BE | `db/seed/V7__seed_promotions.sql`: `CAMPVIVO10`, `GIAM50K` (công khai), `WELCOME50K` (ẩn), 1 chiến dịch đang chạy (~8 sản phẩm, 20%) + 1 sắp diễn ra | PM-03 | Profile dev có đủ dữ liệu; profile test không có |
| - [x] PM-06 | Test | IT schema: `used_count > quantity`, `expiry_date ≤ start_date`, PERCENTAGE > 100, FIXED có `max_discount`, mã chữ thường đều bị DB chặn | PM-04 | IT xanh |

### Phần 2 — Voucher backend (Roadmap N2-16)

| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] PM-07 | BE | `VoucherRules` (thuần Java): kiểm tra theo đúng thứ tự lỗi BR-5, tính mức giảm (HALF_UP, trần `max_discount`, ≤ subtotal) + `VoucherRulesTest` (bảng ca, biên thời gian) | PM-04 | Unit test xanh |
| - [x] PM-08 | BE | `VoucherService` thật: `quote` (chỉ đọc), `consume` (`findByCodeForUpdate` → kiểm tra lại BR-5 kể cả "đã dùng" **sau khi có khoá** → `used_count + 1`), `release`; `VoucherRepository.existsActiveUsage` (native `EXISTS` trên `orders`) | PM-07 | Biên dịch được, chưa nối order |
| - [x] PM-09 | BE | Nối luồng đặt hàng: `Order` nhận `voucherId`; `OrderService.preview`/ước tính phí ship dùng `quote`; `create` dùng `consume` sau khi khoá products/combos; `OrderStateService.apply` trả lượt khi CANCELLED/PAYMENT_FAILED | PM-08 | IT: đặt COD có mã → `used_count + 1`, `orders.voucher_id` đúng; huỷ PENDING / IPN thất bại / job hết hạn → trả lượt |
| - [x] PM-10 | Test | IT đồng thời: 20 luồng, mã còn 1 lượt → đúng 1 đơn; 1 khách 2 đơn song song cùng mã → 1 đơn; dùng lần 2 → `VOUCHER_ALREADY_USED`; dùng lại sau khi đơn trước huỷ → được; không deadlock | PM-09 | IT xanh |
| - [x] PM-11 | BE | `VoucherController`: `POST /vouchers/validate`, `GET /vouchers/available` (dùng `OrderService.checkoutSubtotal`), `GET /vouchers/public`; `SecurityConfig` mở GET `/api/vouchers/public` | PM-09 | IT: mã ẩn không có trong 2 danh sách; `missingAmount`, `reason`, `best` đúng; `/public` gọi được khi chưa đăng nhập |
| - [x] PM-12 | BE | Admin voucher API: `AdminVoucherController/Service`, `VoucherRequest` (validate tiếng Việt), `AdminVoucherResponses` (+ `status` tính sẵn), lọc `q`/`status`, phân trang | PM-04 | IT: xoá mã đã dùng → 409 `VOUCHER_IN_USE`; `quantity < used_count` → 400; mã trùng → 409; mã lưu IN HOA; EMPLOYEE → 403 |

### Phần 3 — Voucher frontend (Roadmap N2-26) — điểm dừng 1

| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] PM-13 | FE | `types/api/promotion.ts`, `services/promotionService.ts`, `services/admin/adminVoucherService.ts`; `errorMessages.ts` thêm mã mới | PM-01 | `tsc` sạch |
| - [x] PM-14 | FE | `/admin/vouchers`: `VoucherListView` (bảng, tìm, lọc trạng thái, phân trang, bật/tắt) + `VoucherFormModal` (ẩn "giảm tối đa" khi FIXED, checkbox "Hiển thị công khai"); `VOUCHER_IN_USE` → gợi ý nút Tắt | PM-12, PM-13 | Thử tay: thêm/sửa/tắt/xoá; lỗi hiện đúng ô |
| - [x] PM-15 | FE | `VoucherList` trong popup `/cart`: mã dùng được trước, nút Áp dụng, mã mờ + lý do ("Mua thêm X đ", "Bạn đã dùng mã này"), nhãn "Tốt nhất"; tải lại khi giỏ/dòng chọn đổi; giữ ô nhập mã | PM-11, PM-13 | Thử tay: chọn mã từ danh sách = nhập tay; loading/empty/error; mobile 375px |
| - [x] PM-16 | FE | PromoBar đọc `GET /vouchers/public` (lỗi/rỗng → slide mock hiện có) | PM-11, PM-13 | Thử tay khi chưa đăng nhập |
| - [ ] PM-17 | Chốt | Chạy `agent-lint.sh` + `agent-test.sh`; người dùng test tay luồng voucher end-to-end; cập nhật `progress.md`, đóng KI-08 | PM-10, PM-14..16 | Người dùng xác nhận; lint/test xanh |

### Phần 4 — Chiến dịch backend (Roadmap N2-13, N2-14, N2-27)

| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] PM-18 | BE | `Product.salePrice` (`@Formula`, biểu thức trong plan); `PricingService.unitPrice(Product)` → `salePrice`; `activeCampaigns(productIds)` (tên/slug/hạn chiến dịch tốt nhất, 1 query) | PM-04 | IT bảng ca: không chiến dịch / 2 chồng (lấy thấp nhất) / hết hạn / chưa bắt đầu / tắt / FIXED > giá → 0 / làm tròn % |
| - [x] PM-19 | BE | Giá sau giảm trong `ItemSummary`, `ProductDetail` (+ trường `campaign`), món trong combo, `itemsTotal` của combo; giỏ hàng + đặt hàng tự theo `PricingService` | PM-18 | IT: `GET /products` có `originalPrice` ≠ `price`; đặt hàng → `order_items.unit_price` = giá sau giảm, `original_price` = giá gốc; combo không đổi giá |
| - [x] PM-20 | BE | `GET /products`: `minPrice`/`maxPrice` lọc theo `salePrice`, `price_asc`/`price_desc` sắp theo `salePrice` (cả khi có từ khoá `q`) | PM-18 | IT: 500k giảm 20% lọt khoảng 300–450k; thứ tự `price_asc` đúng giá sau giảm |
| - [x] PM-21 | BE | `CampaignService` + `CampaignController`: `GET /campaigns/{slug}` (chỉ đang hiệu lực, sản phẩm đang bán, ngược lại 404) | PM-19 | IT: đang chạy → 200 kèm sản phẩm; hết hạn/tắt/chưa bắt đầu → 404 |
| - [x] PM-22 | BE | Admin campaign API: CRUD (slug tự sinh, trùng → `SLUG_ALREADY_EXISTS`; gán `productIds`), danh sách kèm `productCount` + `status`, `GET /admin/campaigns/overlaps` | PM-18 | IT: `endDate ≤ startDate` → 400; gán sản phẩm → giá `GET /products` đổi ngay; overlaps trả đúng chiến dịch chồng; EMPLOYEE → 403 |

### Phần 5 — Chiến dịch frontend (Roadmap N2-15, N2-28) — điểm dừng 2

| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] PM-23 | FE | Giá thống nhất: dùng lại giá gạch + badge `-x%` có sẵn (không tạo `PriceTag`, xem bảng file); chi tiết sản phẩm hiện nhãn chiến dịch + link, đếm ngược có số ngày | PM-19 | Thử tay sản phẩm có/không chiến dịch; không có `%` khi giá bằng nhau |
| - [x] PM-24 | FE | `/promotions/[slug]` đọc `GET /campaigns/{slug}` (banner, mô tả, hạn kết thúc, lưới sản phẩm), 404 khi API 404; bỏ `getMockPromos` cho trang này | PM-21 | Thử tay chiến dịch seed; slug sai/hết hạn → 404 |
| - [x] PM-25 | FE | `adminCampaignService.ts`; `/admin/campaigns` (`CampaignListView`) | PM-22 | Thử tay: lọc trạng thái, nút "Xem trang" |
| - [x] PM-26 | FE | `/admin/campaigns/new` + `[id]`: `CampaignForm` (banner `folder=campaigns`, mô tả rich text) + `CampaignProductPicker` (tìm, chọn nhiều, giá gốc → giá dự kiến, cảnh báo từ `overlaps`) | PM-22, PM-25 | Thử tay: tạo chiến dịch → giá thẻ sản phẩm và `/promotions/[slug]` đổi ngay |
| - [ ] PM-27 | Chốt | `agent-lint.sh` + `agent-test.sh`; người dùng test tay luồng chiến dịch + voucher trên giá đã giảm; cập nhật `progress.md` | PM-20, PM-23..26 | Người dùng xác nhận; lint/test xanh |

### Phần 5b — Lối vào khuyến mãi cho khách (người dùng yêu cầu thêm 2026-10-31)

| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] PM-30 | Docs | `api.md`: `onSale` ở `GET /products`, `GET /campaigns`; `pages.md`: `/promotions` | — | Contract có trước code |
| - [x] PM-31 | BE | `GET /products?onSale=true` (`salePrice < price`); `GET /campaigns` (đang hiệu lực, `productCount` sản phẩm đang bán) | PM-30 | IT: chỉ sản phẩm đang giảm; chồng chiến dịch không trùng dòng; `/campaigns` bỏ chiến dịch hết hạn/sắp tới/tắt |
| - [x] PM-32 | FE | Menu "Dọn Kho 70%" → "Khuyến mãi" (`/promotions`); trang `/promotions` dùng `ListingView` (thêm tuỳ chọn lọc khoảng giá + phân trang, chỉ bật ở trang này), cột trái = chiến dịch đang chạy | PM-31 | Thử tay: lọc giá/sắp xếp/phân trang; 0 sản phẩm → thông báo trống |
| - [x] PM-33 | FE | Khối Flash sale trang chủ lấy sản phẩm đang giảm thật (giảm nhiều trước), nút "Xem tất cả" → `/promotions`, ẩn khối khi không có; PromoBar xen kẽ slide chiến dịch và voucher | PM-31 | Thử tay trang chủ |

### Phần 6 — Tuỳ chọn (chờ người dùng cho phép rebase lên `dev`)

| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [ ] PM-28 | BE | Email chào mừng: `app.promotion.welcome-voucher-code` (rỗng = tắt), gửi sau khi xác thực email; gửi lỗi không chặn xác thực | PM-27 + người dùng cho phép | IT với GreenMail: xác thực xong nhận mail có mã |

### Hoàn tất

| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [ ] PM-29 | Docs | Rà lại docs theo code thật (`documentation-sync`), tick task roadmap (F-07, N2-13..16, N2-26..28), chuyển plan sang `completed/` | PM-27 | Plan khớp những gì đã làm |

Mở PR là việc của người dùng (đã chốt không đụng nhánh khác) — agent không tự push/mở PR.
