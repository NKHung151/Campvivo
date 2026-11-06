# Plan: Đánh giá sản phẩm / combo (+ kiểm duyệt tự động, quản trị ẩn/xoá)

- **Ngày tạo:** 2026-11-06
- **Người/agent phụ trách:** Người 4, nhánh `feature/review-product` (tách từ `dev` @ `0899757`)
- **Tier:** 2 (FR-18; BR-10; D-09)
- **Module backend liên quan:** `review` (chính, đang trống), `order` (kiểm tra "đã mua", `canReview` ở chi tiết đơn),
  `catalog` + `combo` + `cart` (điểm trung bình, số lượt thật thay cho `null, 0`), `auth` (`SecurityConfig`),
  `common` (`ErrorCode`)
- **Module/feature frontend liên quan:** `features/review` (mới), `features/product` (`ProductTabs`, `ProductInfo`),
  `features/combo` (`ComboPage`), `features/account` (`MyOrderDetail`), `features/catalog` (`ProductCard`),
  `services/catalogService.ts`, admin `/admin/reviews` (mới), `lib/admin/nav.ts`

## Mục tiêu
- **Khách hàng** đã nhận hàng (đơn COMPLETED) viết, sửa, xoá đánh giá **1–5 sao + nhận xét tuỳ chọn** cho sản phẩm/combo,
  từ trang chi tiết mặt hàng hoặc từ từng dòng hàng ở chi tiết đơn.
- **Mọi người** xem điểm trung bình, số lượt, phân bố sao, danh sách đánh giá (lọc theo số sao) ở trang sản phẩm/combo;
  thẻ mặt hàng hiển thị sao thật.
- **Hệ thống** tự phát hiện ngôn từ không lành mạnh bằng từ điển, **làm mờ cả đánh giá**; người xem bấm "Hiển thị" mới đọc.
- **Employee/Admin** ẩn/hiện lại, đánh dấu an toàn đánh giá; **Admin** xoá đánh giá.

## Phạm vi
- **Trong phạm vi:**
  1. Schema `reviews` (+ cột kiểm duyệt, cột ẩn) — migration `V8__reviews.sql`.
  2. API khách: viết/sửa/xoá, xem đánh giá của mình cho 1 mặt hàng; API công khai: danh sách (lọc sao, phân trang) + tổng quan
     (trung bình, số lượt, phân bố sao).
  3. Điểm trung bình/số lượt thật trong `ItemSummary`, `ProductDetail`, `ComboSummary`, `ComboDetail`, giỏ hàng.
  4. `canReview` + `myReviewId` cho từng dòng hàng ở chi tiết đơn.
  5. Kiểm duyệt tự động bằng **từ điển chính thức** tiếng Việt (file trong code) + bộ chuẩn hoá chống lách.
  6. Quản trị `/admin/reviews`: lọc, ẩn (bắt buộc lý do), hiện lại, đánh dấu an toàn, xoá (Admin).
  7. Frontend: khối đánh giá dùng chung cho sản phẩm + combo, hộp thoại đánh giá ở chi tiết đơn, sửa hiển thị sao trên thẻ.
- **Ngoài phạm vi (cố tình không làm):**
  - Upload ảnh/video trong đánh giá (người dùng bỏ 2026-11-06).
  - Lớp kiểm duyệt bằng AI; admin sửa từ điển trên giao diện (cần bảng mới) → "hướng phát triển".
  - Tab/trang "Đánh giá của tôi" `/account/reviews` và `GET /users/me/reviews` (người dùng bỏ — D-34).
  - Shop trả lời đánh giá, bấm "hữu ích", sắp xếp khác ngoài mới nhất, thông báo cho khách khi đánh giá bị ẩn.
  - Seed đánh giá mẫu (chưa có seed đơn hàng COMPLETED để đánh giá hợp lệ).

## Quyết định thiết kế (đã chốt với người dùng 2026-11-05 → 2026-11-06)
| # | Vấn đề | Chọn | Ghi vào |
|---|---|---|---|
| 1 | Nơi khách quản lý đánh giá của mình | **Bỏ** `/account/reviews`. Xem/sửa/xoá ở trang chi tiết mặt hàng và ở dòng hàng của chi tiết đơn | D-34 (mới) |
| 2 | Độ dài nhận xét | Chỉ bắt buộc chọn sao; nhận xét tuỳ chọn, ≤ 2000 ký tự (bỏ luật "≥ 16 ký tự" của mock) | BR-10 (giữ) |
| 3 | Tên người viết | Họ tên đầy đủ (`users.full_name`) | api.md (giữ) |
| 4 | Lọc theo số sao | Có: `?rating=1..5` + endpoint tổng quan trả phân bố sao | D-32 (mới) |
| 5 | Ẩn/xoá của nhân viên | Employee + Admin: Ẩn (bắt buộc lý do) / Hiện lại / Đánh dấu an toàn. Chỉ Admin: Xoá cứng. Đánh giá ẩn: không hiện công khai, **không tính** vào điểm; chủ thấy kèm lý do, **không sửa được, chỉ xoá**; xoá xong viết lại được (đi qua kiểm duyệt như mới) | D-31 (mới) |
| 6 | Phát hiện nội dung không lành mạnh | **Chỉ từ điển** (file trong code) + chuẩn hoá chống lách; chạy đồng bộ khi viết/sửa; **không chặn** đăng | D-33 (mới) |
| 7 | Cách làm mờ | **Làm mờ cả nội dung** (CSS blur + nút "Hiển thị"); tên, sao, ngày vẫn hiện. Chủ đánh giá thấy rõ + ghi chú. Đánh giá mờ **vẫn tính** điểm | D-33 |
| 8 | Điểm trung bình / số lượt | `@Formula` trên `Product` và `ProductCombo` (như `salePrice` D-30), bỏ qua đánh giá ẩn, làm tròn 1 chữ số | D-35 (mới) |
| 9 | Kiểm tra "đã mua" | `EXISTS` trên `order_items` ⨝ `orders` với `status = COMPLETED` của chính user (RETURNED không tính); mua combo ≠ mua sản phẩm con | BR-10 (giữ) |
| 10 | Đánh giá trùng khi gửi đồng thời | Kiểm tra trước + unique index một phần là chốt cuối; bắt `DataIntegrityViolationException` → `REVIEW_ALREADY_EXISTS` | — |

**Lý do chọn #8:** giữ đúng BR-10 ("tính bằng truy vấn, không lưu cột tổng hợp"); catalog/combo/cart không phải gọi sang module
`review`; dùng được ngay khi map DTO ở mọi nơi. Đánh đổi: entity `Product`/`ProductCombo` chứa SQL tham chiếu bảng `reviews`
(cùng kiểu đánh đổi D-30); mỗi dòng danh sách thêm 2 subquery — có index `reviews(product_id)`, `reviews(combo_id)` nên chấp nhận được.

**Lý do chọn #6:** miễn phí, chạy offline, kết quả xác định → viết test được, dễ trình bày khi bảo vệ. Giới hạn đã biết: không
hiểu ngữ cảnh, bỏ sót xúc phạm không dùng từ tục và từ tục viết không dấu trùng từ thường (vd. `lon`) → nhân viên xử lý bằng **Ẩn**.

## Nghiệp vụ chi tiết (bản đã duyệt)

### Quy tắc chung
- Chỉ **CUSTOMER** viết/sửa/xoá. Guest xem. Employee/Admin không viết.
- Đủ điều kiện = có ≥ 1 đơn **của chính mình** ở `COMPLETED` chứa dòng `product_id` (hoặc `combo_id`) đó.
- 1 đánh giá / (user, sản phẩm) và / (user, combo), dù mua nhiều lần.
- `updated_at` **chỉ đổi khi chủ sửa** (hiển thị "(đã chỉnh sửa)" khi `updated_at > created_at`); hành động của nhân viên ghi
  `hidden_at`/`hidden_by`, không đụng `updated_at`.
- Mỗi lần viết/sửa chạy lại kiểm duyệt: có từ cấm → `SENSITIVE`; không → `SAFE`. Sửa xoá luôn trạng thái `CLEARED` trước đó.
- Nội dung chỉ có khoảng trắng → lưu `null`.

### Trang và tương tác
1. **`/products/[slug]`**
   - Đầu trang (`ProductInfo`): sao theo trung bình + link "Xem N đánh giá" (cuộn tới tab Đánh giá). Chưa có → "Chưa có đánh giá".
   - Tab "Đánh giá" (`ReviewSection`): điểm TB (vd. 4.3/5) + tổng lượt; **thanh phân bố 5★→1★** (bấm để lọc); nút lọc
     "Tất cả · 5★ · 4★ · 3★ · 2★ · 1★"; danh sách mới nhất trước, "Xem thêm" (10/lần); mỗi dòng: họ tên, ngày, sao, nội dung,
     "(đã chỉnh sửa)"; đánh giá `isSensitive` → nội dung mờ + "Đánh giá này có thể chứa ngôn từ không phù hợp" + nút **Hiển thị**.
   - Khu viết đánh giá theo người xem:
     | Người xem | Hiển thị |
     |---|---|
     | Guest | "Đăng nhập để đánh giá" → `/login?redirect=<trang hiện tại>` |
     | Customer chưa đủ điều kiện | "Bạn có thể đánh giá sau khi nhận hàng thành công", không có form |
     | Customer đủ điều kiện, chưa viết | Form: chọn sao (bắt buộc), ô nhận xét (bộ đếm /2000), nút **Gửi đánh giá** |
     | Customer đã viết | "Đánh giá của bạn" ở đầu (không lặp lại trong danh sách), nút **Sửa** / **Xoá**; nếu đang mờ: ghi chú "Đánh giá của bạn đang bị làm mờ vì chứa ngôn từ không phù hợp"; nếu bị ẩn: "Đánh giá của bạn đã bị ẩn: <lý do>", chỉ còn **Xoá** |
     | Employee/Admin | Chỉ xem |
   - Gửi/sửa/xoá thành công → thông báo + tải lại tổng quan và danh sách. Xoá có hỏi xác nhận.
2. **`/combos/[slug]`** — dùng chung `ReviewSection` với `itemType = COMBO` (trang này hiện chưa có phần đánh giá).
3. **Thẻ sản phẩm/combo** (danh sách, tìm kiếm, trang chủ, khuyến mãi, so sánh) — sao thật; chưa có đánh giá → sao trống
   (bỏ mặc định 5 sao đầy hiện nay).
4. **`/account/orders/[id]`** — chỉ đơn `COMPLETED`, **mỗi dòng hàng** có nút riêng:
   `myReviewId = null` → **"Viết đánh giá"**; có → **"Xem đánh giá"** (mở ở chế độ xem, có Sửa/Xoá; bị ẩn → chỉ Xoá).
   Hộp thoại (`ReviewDialog`): ảnh + tên mặt hàng, chọn sao, nhận xét, Gửi. Nhận `REVIEW_ALREADY_EXISTS` → tải đánh giá đó và
   chuyển sang sửa. Thành công → nút đổi trạng thái tại chỗ. Đơn trạng thái khác: không có nút.
5. **`/admin/reviews`** (Employee + Admin)
   - Bảng: mặt hàng (link), khách (họ tên + email), sao, nội dung (rút gọn, bấm xem đủ), từ bị bắt, trạng thái, ngày.
   - Lọc: `q` (tên/email khách hoặc tên mặt hàng), loại (sản phẩm/combo), số sao, trạng thái
     `VISIBLE | HIDDEN | SENSITIVE` (SENSITIVE = đang mờ, chưa ẩn). Phân trang.
   - Hành động: **Ẩn** (modal bắt buộc lý do ≤ 500 ký tự), **Hiện lại**, **Đánh dấu an toàn** (chỉ khi `SENSITIVE`),
     **Xoá** (chỉ Admin, hỏi xác nhận).

## Thiết kế dữ liệu — `V8__reviews.sql`
(Số V8: `migration/` dùng V1, V2, V5, V6; `seed/` dùng V3, V4, V7 — cùng một dãy Flyway. Đã kiểm tra mọi nhánh trên `origin`
2026-11-06: chưa ai dùng V8. Kiểm lại ngay trước khi mở PR.)

| Cột | Kiểu | Ràng buộc / ghi chú |
|---|---|---|
| id | BIGINT | PK identity |
| user_id | BIGINT | NOT NULL, FK → users(id) |
| product_id | BIGINT | N, FK → products(id) `ON DELETE CASCADE` |
| combo_id | BIGINT | N, FK → product_combos(id) `ON DELETE CASCADE` |
| rating | SMALLINT | NOT NULL, `CHECK (rating BETWEEN 1 AND 5)` |
| content | VARCHAR(2000) | N |
| moderation_status | VARCHAR(20) | NOT NULL, mặc định `'SAFE'`, `CHECK IN ('SAFE','SENSITIVE','CLEARED')` — **mới** |
| flagged_terms | VARCHAR(500) | N — các mục từ điển bị bắt, cách nhau dấu phẩy — **mới** |
| is_hidden | BOOLEAN | NOT NULL, mặc định false — **mới** |
| hidden_reason | VARCHAR(500) | N — **mới** |
| hidden_by | BIGINT | N, FK → users(id) — **mới** |
| hidden_at | timestamptz | N — **mới** |
| created_at, updated_at | timestamptz | NOT NULL |

- `CHECK (num_nonnulls(product_id, combo_id) = 1)`
- `CHECK (NOT is_hidden OR (hidden_reason IS NOT NULL AND hidden_at IS NOT NULL))`
- Unique một phần: `(user_id, product_id) WHERE product_id IS NOT NULL`, `(user_id, combo_id) WHERE combo_id IS NOT NULL`
- Index: `(product_id, created_at DESC) WHERE product_id IS NOT NULL`, `(combo_id, created_at DESC) WHERE combo_id IS NOT NULL`
  (vừa phục vụ danh sách vừa phục vụ `@Formula`).
- Vẫn 22 bảng — chỉ thêm cột.

## Thiết kế API (cập nhật `api.md` TRƯỚC khi code — bước 0)

### Công khai
| Method | Path | Quyền | Body → Response |
|---|---|---|---|
| GET | `/products/{id}/reviews` | Public | `?rating=1..5&page&limit` (limit mặc định 10) → `PageResponse<Review>`; bỏ đánh giá ẩn; mới nhất trước |
| GET | `/products/{id}/reviews/summary` | Public | → `ReviewSummary` (**mới**) |
| GET | `/combos/{id}/reviews`, `/combos/{id}/reviews/summary` | Public | như sản phẩm |

Mặt hàng không tồn tại hoặc đang tắt → 404 `NOT_FOUND` (cùng quy tắc với `GET /products/{slug}`).
> Đổi so với docs cũ: response `{averageRating, reviewCount, items}` tách thành danh sách (`PageResponse`, đúng format phân trang
> chung ở api.md mục 1) + `summary` (có thêm phân bố sao). Ghi ở D-32.

### Khách hàng
| Method | Path | Quyền | Body → Response |
|---|---|---|---|
| GET | `/reviews/mine` | Customer | `?productId=\|comboId=` (đúng 1) → `MyReview \| null` (**mới**, thay `GET /users/me/reviews`) |
| POST | `/reviews` | Customer | `{productId?, comboId?, rating, content?}` → 201 `MyReview` |
| PUT | `/reviews/{id}` | Customer (chủ) | `{rating, content?}` → `MyReview` |
| DELETE | `/reviews/{id}` | Customer (chủ) | → 200 |

Lỗi: `VALIDATION_ERROR` (sao ngoài 1–5, nội dung > 2000, không/cả hai `productId` + `comboId`), `NOT_FOUND` (mặt hàng không tồn
tại; sửa/xoá đánh giá không phải của mình), `NOT_PURCHASED` 403, `REVIEW_ALREADY_EXISTS` 409, **`REVIEW_HIDDEN` 409 (mới)** —
sửa đánh giá đã bị ẩn ("Đánh giá đã bị ẩn, bạn chỉ có thể xoá.").

### Admin
| Method | Path | Quyền | Body → Response |
|---|---|---|---|
| GET | `/admin/reviews` | Employee, Admin | `?q&itemType=PRODUCT\|COMBO&rating&status=VISIBLE\|HIDDEN\|SENSITIVE&page&limit` → `PageResponse<AdminReview>`, mới nhất trước |
| PATCH | `/admin/reviews/{id}/hide` | Employee, Admin | `{reason}` (1–500 ký tự) → `AdminReview` (đã ẩn → ghi đè lý do) |
| PATCH | `/admin/reviews/{id}/unhide` | Employee, Admin | → `AdminReview` (xoá `hidden_*`) |
| PATCH | `/admin/reviews/{id}/clear-sensitive` | Employee, Admin | → `AdminReview` (`SENSITIVE` → `CLEARED`; trạng thái khác → giữ nguyên) |
| DELETE | `/admin/reviews/{id}` | **Admin** (`@PreAuthorize`) | → 200 |

### Kiểu dữ liệu
```ts
interface Review { id: number; userFullName: string; rating: number; content: string | null;
  isSensitive: boolean; createdAt: string; updatedAt: string; }
interface MyReview extends Review { itemType: ItemType; itemId: number;
  isHidden: boolean; hiddenReason: string | null; }
interface ReviewSummary { averageRating: number | null; reviewCount: number;
  ratingCounts: { 1: number; 2: number; 3: number; 4: number; 5: number }; }
interface AdminReview { id: number; rating: number; content: string | null;
  user: { id: number; fullName: string; email: string };
  item: { type: ItemType; id: number; name: string; slug: string };
  moderationStatus: "SAFE" | "SENSITIVE" | "CLEARED"; flaggedTerms: string[];
  isHidden: boolean; hiddenReason: string | null; hiddenByName: string | null; hiddenAt: string | null;
  createdAt: string; updatedAt: string; }
// OrderDetail.items[] thêm: myReviewId: number | null   (canReview giữ nguyên tên)
```
- `isSensitive = moderationStatus == SENSITIVE`. `canReview = order.status == COMPLETED && myReviewId == null`.
- `Review.content` vẫn trả về khi `isSensitive` (làm mờ phía giao diện — đã chốt).

## Kiểm duyệt bằng từ điển

### Thành phần
- `review/moderation/ContentModerator.java` — interface `ModerationResult check(String content)`
  (`ModerationResult(boolean sensitive, List<String> flaggedTerms)`).
- `review/moderation/DictionaryModerator.java` — cài đặt duy nhất; nạp từ điển một lần khi khởi động (`@PostConstruct`), lỗi
  định dạng file → app không khởi động (fail fast).
- `review/moderation/TextNormalizer.java` — các bước chuẩn hoá (thuần hàm, test riêng).
- File `backend/src/main/resources/moderation/vi-banned-words.txt` — **từ điển chính thức**.

### Định dạng file từ điển
```
# Dòng bắt đầu bằng # là chú thích. Mỗi dòng một mục (có thể nhiều từ). Không phân biệt hoa thường.
[accented]   # so trên chữ ĐÃ chuẩn hoá nhưng CÒN dấu — từ mà bỏ dấu sẽ trùng từ thường (lồn ↔ lon nước, ngu ↔ ngủ)
[plain]      # so trên chữ đã BỎ dấu — từ/cụm bỏ dấu vẫn không trùng từ thường (dit me, du ma)
[abbr]       # viết tắt/teencode, khớp NGUYÊN token trên chữ bỏ dấu (dm, dkm, vcl, clm)
[allow]      # cụm hợp lệ hay bị bắt nhầm; được xoá khỏi văn bản trước khi so khớp
```

### Thuật toán
1. Unicode **NFC** (gõ dấu tổ hợp vs dựng sẵn), bỏ ký tự vô hình (zero-width), chữ thường.
2. Gộp chữ cái đơn bị chèn ký tự: chuỗi token 1 ký tự cách nhau bởi `. - _ * , ` hoặc khoảng trắng → nối lại
   (`đ.m` / `đ m` / `d-k-m` → `đm` / `dkm`).
3. Token vừa có chữ vừa có số/ký hiệu → đổi leetspeak (`0→o, 1→i, 3→e, 4→a, 5→s, 7→t, @→a, $→s`); token toàn số giữ nguyên.
4. Gộp chữ cái lặp ≥ 3 lần thành 1 (`nguuuu` → `ngu`, `vlllll` → `vl`).
5. Xoá các cụm `[allow]`.
6. Bản **có dấu** → so `[accented]` theo ranh giới từ. Bản **bỏ dấu** (`đ→d`, bỏ dấu thanh/mũ) → so `[plain]` theo ranh giới
   từ và `[abbr]` theo nguyên token. Ranh giới từ = không có chữ/số liền trước/sau (`dm` không khớp trong `admin`).
7. Có ≥ 1 mục khớp → `SENSITIVE`, `flaggedTerms` = các mục gốc (không trùng lặp, cắt ≤ 500 ký tự).

### Phạm vi nội dung từ điển (chính thức, không phải bản nháp)
Chỉ gồm: chửi thề/thô tục; từ chỉ bộ phận/hành vi tình dục dùng theo nghĩa tục; lăng mạ nhân phẩm/trí tuệ; kỳ thị vùng miền,
dân tộc, tôn giáo, giới, xu hướng tính dục, khuyết tật; đe doạ bạo lực. Mỗi nhóm có bản có dấu, không dấu, teencode và các
biến thể lách phổ biến. **Không** đưa vào từ chê bai sản phẩm/dịch vụ ("tệ", "dỏm", "lừa đảo", "hàng fake", "phí tiền").
Đối chiếu nguồn mở (danh sách từ tục tiếng Việt trên GitHub; các bộ dữ liệu nghiên cứu ViHSD/ViHOS của UIT) — chỉ dùng nguồn có
giấy phép phù hợp, ghi nguồn trong phần đầu file.

### Bộ test bắt buộc (`DictionaryModeratorTest`, tham số hoá)
- **Phải bắt** (≥ 40 câu): mỗi nhóm nội dung ở trên, mỗi kiểu lách (dấu chấm/gạch/khoảng trắng chen, leetspeak, lặp chữ, viết
  hoa, gõ dấu tổ hợp), vd. "Đ.m hàng như c*t", "ngu vl", "D K M shop", "vcllll".
- **Không được bắt** (≥ 40 câu): đánh giá chê bai gay gắt nhưng sạch; câu chứa từ trùng khi bỏ dấu ("1 lon nước", "ngủ ngon",
  "cú pháp", "con cua", "bướm đêm bay vào lều"…); chuỗi chứa viết tắt ("admin", "html", "cm", "ml", "kg", "dmx").
- Mỗi lần sửa từ điển phải chạy lại test; thêm mục mới → thêm ít nhất 1 câu "phải bắt" và kiểm tra nhóm "không được bắt".

## File sẽ tạo/sửa
| File | Tạo mới / Sửa | Ghi chú |
|---|---|---|
| `backend/src/main/resources/db/migration/V8__reviews.sql` | Tạo | Bảng `reviews` + CHECK + unique một phần + index |
| `backend/.../review/Review.java`, `ModerationStatus.java` | Tạo | Entity; `updated_at` gán tay khi chủ sửa (không `@UpdateTimestamp`) |
| `backend/.../review/ReviewRepository.java` | Tạo | Tìm theo item + `rating` (phân trang), đếm theo sao, tìm của user cho item, theo `user_id` + `id` |
| `backend/.../review/ReviewService.java` | Tạo | Viết/sửa/xoá, kiểm tra đã mua, kiểm duyệt, `REVIEW_HIDDEN` |
| `backend/.../review/ReviewQueryService.java` | Tạo | Danh sách công khai, summary, `mine`; `reviewIdsByUser(userId, productIds, comboIds)` cho order |
| `backend/.../review/ReviewController.java` | Tạo | `/reviews`, `/reviews/mine`, `/products/{id}/reviews[/summary]`, `/combos/{id}/reviews[/summary]` |
| `backend/.../review/dto/*` | Tạo | `ReviewResponse`, `MyReviewResponse`, `ReviewSummaryResponse`, `ReviewRequests` |
| `backend/.../review/moderation/*` | Tạo | `ContentModerator`, `DictionaryModerator`, `TextNormalizer`, `ModerationResult` |
| `backend/src/main/resources/moderation/vi-banned-words.txt` | Tạo | Từ điển chính thức |
| `backend/.../review/admin/AdminReviewController.java`, `AdminReviewService.java`, `AdminReviewResponses.java` (`AdminReview`, `HideRequest`, `Query`, `StatusFilter` — gộp một file như `order/admin`) | Tạo | API admin; xoá có `@PreAuthorize("hasRole('ADMIN')")` |
| `backend/.../order/OrderRepository.java` (hoặc repository của `OrderItem`) | Sửa | `existsCompletedPurchase(userId, productId \| comboId)` |
| `backend/.../order/OrderQueryService.java` | Sửa | `hasCompletedPurchase(...)` cho review; gắn `myReviewId`/`canReview` vào chi tiết đơn (1 truy vấn cho cả đơn) |
| `backend/.../order/dto/OrderDetailResponse.java` | Sửa | `Item` thêm `myReviewId`; bỏ `canReview = false` cứng |
| `backend/.../catalog/Product.java`, `combo/ProductCombo.java` | Sửa | `@Formula` `averageRating`, `reviewCount` |
| `backend/.../catalog/dto/ItemSummary.java`, `ProductDetailResponse.java`, `combo/dto/ComboSummary.java`, `ComboDetailResponse.java`, `cart/CartService.java` | Sửa | Thay `null, 0` bằng giá trị thật |
| `backend/.../common/exception/ErrorCode.java` | Sửa | Thêm `REVIEW_HIDDEN` |
| `backend/.../auth/SecurityConfig.java` | Sửa | Bỏ `/api/users/me/reviews`; `/api/reviews/**` đã có; GET công khai đã phủ `/api/products/**`, `/api/combos/**` |
| `backend/src/test/java/vn/campvivo/review/*` | Tạo | Xem "Cách test" |
| `frontend/src/types/api/review.ts` | Tạo | Kiểu ở trên |
| `frontend/src/types/api/order.ts` | Sửa | `myReviewId` |
| `frontend/src/services/reviewService.ts`, `services/admin/adminReviewService.ts` | Tạo | Gọi API |
| `frontend/src/components/features/review/` | Tạo | `ReviewSection`, `ReviewSummaryBar`, `ReviewList`, `ReviewItem` (làm mờ), `ReviewForm`, `MyReviewBox`, `ReviewDialog`, `StarRatingInput`, `StarRatingDisplay` |
| `frontend/src/components/features/product/ProductTabs.tsx`, `ProductInfo.tsx`, `ProductPage.tsx` | Sửa | Bỏ mock `localStorage`, dùng `ReviewSection`; "Chưa có đánh giá" |
| `frontend/src/components/features/combo/ComboPage.tsx` | Sửa | Thêm `ReviewSection` |
| `frontend/src/components/features/account/MyOrderDetail.tsx` | Sửa | Nút theo dòng hàng + `ReviewDialog` |
| `frontend/src/components/features/catalog/ProductCard.tsx`, `services/catalogService.ts` | Sửa | Bỏ mặc định `?? 100` / `\|\| 100` (5 sao giả) |
| `frontend/src/types/shop.ts` | Sửa | Bỏ `Review` mock nếu không còn dùng |
| `frontend/src/app/(admin)/admin/reviews/page.tsx`, `components/admin/review/ReviewListView.tsx`, `HideReviewModal.tsx` | Tạo | Trang admin |
| `frontend/src/lib/admin/nav.ts` | Sửa | Mục "Đánh giá" (STAFF) |
| `docs/...` (xem bước 0) và `.agents/progress.md` | Sửa | |

## Thứ tự thực hiện
Làm và commit theo từng phần trên `feature/review-product` (người dùng tự add/commit/push). Đề xuất 1 PR vào `dev`; nếu PR quá
lớn thì tách PR 1 = phần 0–4, PR 2 = phần 5–7.

0. **Docs trước** — `api.md` (4.2 bỏ `/users/me/reviews`; 4.3 reviews + summary; 4.6 thêm `/reviews/mine`, lỗi; mục admin
   reviews; kiểu dữ liệu; `OrderDetail.items[].myReviewId`; mã lỗi `REVIEW_HIDDEN`), `database.md` (3.17 cột mới, BR-10 bổ sung
   ẩn/kiểm duyệt, số migration V8), `features.md` (FR-18 + quản trị đánh giá), `use-cases.md`, `pages.md` (bỏ `/account/reviews`,
   thêm `/admin/reviews`, sửa `/products/[slug]`, `/combos/[slug]`), `authentication.md` (dòng quyền quản trị đánh giá),
   `decisions.md` D-31…D-35.
1. **Schema:** `V8__reviews.sql`, entity, repository. Test schema (CHECK, unique một phần).
2. **Kiểm duyệt:** `TextNormalizer`, `DictionaryModerator`, **từ điển chính thức**, `DictionaryModeratorTest`. Làm trước
   service để service dùng ngay.
3. **API khách + công khai:** `ReviewService`, `ReviewQueryService`, `ReviewController`, kiểm tra đã mua qua `order`,
   `REVIEW_HIDDEN`. Integration test.
4. **Tích hợp hệ thống:** `@Formula` điểm/số lượt + thay `null, 0` ở 5 nơi; `myReviewId`/`canReview` ở chi tiết đơn. Test.
5. **API admin:** `AdminReviewController/Service`. Integration test (quyền, ẩn/hiện, đánh dấu an toàn, xoá).
6. **Frontend khách:** types, service, `features/review/*`, trang sản phẩm, trang combo, chi tiết đơn, sửa sao trên thẻ.
7. **Frontend admin:** `/admin/reviews`, nav.
8. **Hoàn tất:** `./scripts/agent-lint.sh`, `./scripts/agent-test.sh`, kiểm tra UI (responsive, loading/empty/error), cập nhật
   trạng thái `pages.md`, `.agents/progress.md`, kiểm lại số V8 trên `origin`.

## Ảnh hưởng tới hệ thống hiện có
- **Không** đụng tồn kho, giá, trạng thái đơn, thanh toán.
- `ItemSummary`, `ProductDetail`, `ComboSummary`, `ComboDetail`, giỏ hàng: `averageRating`/`reviewCount` đổi từ `null/0` cứng
  sang giá trị thật — cùng tên field, frontend đang đọc sẵn.
- `OrderDetail.items[]` thêm `myReviewId`; `canReview` đổi từ luôn `false` sang giá trị thật.
- API đổi so với docs (chưa ai cài): bỏ `GET /users/me/reviews`; tách response danh sách đánh giá thành list + summary.
- `Product`/`ProductCombo` thêm 2 subquery mỗi lần load → kiểm tra không làm chậm `GET /products` (index đã có).
- Giao diện: bỏ mock đánh giá `localStorage` (`cv-reviews:<slug>`) ở trang sản phẩm; thẻ hết hiện 5 sao giả.

## Cách test
- **Unit:** `TextNormalizerTest` (từng bước chuẩn hoá); `DictionaryModeratorTest` (≥ 40 câu phải bắt, ≥ 40 câu không được bắt;
  file từ điển hỏng → lỗi khi nạp).
- **Schema:** rating 0/6 bị chặn; cả `product_id` + `combo_id` hoặc không có cả hai bị chặn; trùng (user, sản phẩm) bị chặn;
  `is_hidden = true` thiếu lý do bị chặn.
- **Integration (Testcontainers) — `ReviewFlowIntegrationTest`:**
  - Đơn COMPLETED → viết được (201); chưa mua / đơn PENDING, SHIPPING / đơn RETURNED → 403 `NOT_PURCHASED`.
  - Mua combo nhưng đánh giá sản phẩm con → 403.
  - Viết lần 2 → 409 `REVIEW_ALREADY_EXISTS`; xoá rồi viết lại → 201.
  - Sửa/xoá đánh giá của người khác → 404; sao 0 hoặc nội dung 2001 ký tự → 400.
  - Nội dung có từ cấm → `isSensitive = true`; sửa sạch → `false`; `CLEARED` rồi sửa lại có từ cấm → `SENSITIVE`.
  - Danh sách: mới nhất trước, `?rating=` đúng, phân trang đúng; `summary` đúng phân bố; đánh giá ẩn không xuất hiện và không tính.
  - `averageRating`/`reviewCount` trong `GET /products/{slug}`, `GET /products`, `GET /combos/{slug}` đúng (bỏ đánh giá ẩn).
  - Chi tiết đơn: `canReview`/`myReviewId` đúng cho từng dòng (sản phẩm + combo); đơn chưa COMPLETED → `canReview = false`.
  - `GET /reviews/mine` trả đánh giá kể cả khi bị ẩn (có `hiddenReason`); chưa viết → `null`.
- **Integration — `AdminReviewIntegrationTest`:** Employee ẩn (thiếu lý do → 400), hiện lại, đánh dấu an toàn; khách sửa đánh giá
  bị ẩn → 409 `REVIEW_HIDDEN`, xoá được; Employee xoá → 403, Admin xoá → 200; Customer gọi `/admin/reviews` → 403; lọc
  `status`/`rating`/`q` đúng.
- **Security:** GET danh sách/summary không cần token; POST `/reviews` không token → 401, token Employee → 403.
- **Frontend (thủ công):** Guest / khách chưa mua / khách đủ điều kiện / khách đã viết / đánh giá bị mờ / bị ẩn trên trang sản
  phẩm và combo; lọc sao; "Xem thêm"; hộp thoại ở chi tiết đơn nhiều dòng hàng; admin ẩn/hiện/xoá theo role; màn hình hẹp.

## Trạng thái
- [x] Đang làm — xong code 2026-11-06
- [x] Đã test — backend 490/490 (`mvn test`), `agent-lint.sh` sạch, frontend `tsc --noEmit` sạch; người dùng thử tay trang sản
  phẩm, chi tiết đơn, `/admin/reviews` và góp ý giao diện (đã sửa: icon menu, menu "⋯" cho thao tác, khung riêng mỗi đánh giá,
  `.ttl` của `base.css` ép chữ thường). `npm run build` **chưa chạy** vì người dùng đang chạy `npm run dev` (dùng chung `.next`)
  — chạy trước khi mở PR.
- [x] Đã cập nhật docs/
- [ ] Đã mở PR — việc của người dùng; kiểm lại V8 trên `origin` ngay trước khi mở (2026-11-06: mọi nhánh vẫn chưa dùng V8,
  `origin/dev` chưa có commit mới sau `0899757`)

## Danh sách task

**Quy ước làm việc (người dùng chốt 2026-11-06):** mọi task làm trên nhánh `feature/review-product`. KHÔNG merge, rebase, push
hay sửa nhánh khác. **Chưa `git add`/commit/push** cho tới khi người dùng yêu cầu. Mỗi task xong: tick ô. Mỗi "điểm dừng" =
người dùng kiểm tra trước khi sang phần sau.

Cột "Roadmap" ánh xạ sang task gốc trong `backend-roadmap.md`: **F-09** (phần bảng `reviews`), **N4-01** (review backend).

### Bước 0 — Contract + docs

| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] RV-01 | Docs | `api.md`: 4.2 bỏ `GET /users/me/reviews`; 4.3 `GET /products/{id}/reviews` + `/combos/{id}/reviews` đổi sang `PageResponse<Review>` có `?rating=`, thêm `.../reviews/summary`; 4.6 thêm `GET /reviews/mine`, response `MyReview`, bảng lỗi; mục admin thêm `/admin/reviews` (GET, hide, unhide, clear-sensitive, DELETE); kiểu `Review` (+`isSensitive`), `MyReview`, `ReviewSummary`, `AdminReview`; `OrderDetail.items[].myReviewId`; mã lỗi `REVIEW_HIDDEN` (409) | — | Mọi endpoint/kiểu ở mục "Thiết kế API" có trong `api.md`; không còn `ReviewMine`/`/users/me/reviews` |
| - [x] RV-02 | Docs | `database.md`: 3.17 thêm 6 cột + 2 CHECK + index; BR-10 bổ sung: đánh giá ẩn không hiện/không tính điểm, chủ chỉ xoá được; kiểm duyệt bằng từ điển, không chặn đăng; `updated_at` chỉ đổi khi chủ sửa; ghi số migration V8 | — | 3.17 + BR-10 khớp mục "Thiết kế dữ liệu" và "Quy tắc chung" |
| - [x] RV-03 | Docs | `decisions.md`: D-31 (ẩn/xoá + quyền Employee/Admin), D-32 (lọc sao + tách list/summary), D-33 (từ điển + làm mờ cả nội dung, giới hạn đã biết), D-34 (bỏ `/account/reviews` — chức năng *Manage review* dời sang trang chi tiết mặt hàng + chi tiết đơn), D-35 (`@Formula` điểm/số lượt) | — | 5 quyết định có Lý do + Đánh đổi |
| - [x] RV-04 | Docs | `features.md` FR-18 (lọc sao, làm mờ, ngoại lệ `REVIEW_HIDDEN`) + mục quản trị đánh giá; `use-cases.md` (dòng *Manage review* → route mới, thêm *Moderate review* cho Employee/Admin); `pages.md` (bỏ `/account/reviews`, thêm `/admin/reviews`, cập nhật `/products/[slug]`, `/combos/[slug]`, `/account/orders/[id]`); `authentication.md` (dòng quyền quản trị đánh giá, Xoá chỉ Admin); thêm `user-flows.md` mục 9 | RV-03 | Tra FR-18 → API → route → bảng ra đúng; **người dùng duyệt docs** (điểm dừng 0) |

### Phần 1 — Schema (Roadmap F-09, phần `reviews`)

| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] RV-05 | BE | Kiểm lại trên `origin` chưa nhánh nào dùng V8; tạo `V8__reviews.sql` (bảng, FK, 4 CHECK, 2 unique một phần, 2 index) | RV-02 | Backend khởi động sạch, Flyway báo V8 thành công |
| - [x] RV-06 | BE | `ModerationStatus` (enum), `Review` (entity: factory `create`, `edit`, `hide`, `unhide`, `clearSensitive`; `updated_at` chỉ đổi trong `edit`), `ReviewRepository` (khung, query thêm ở RV-12) | RV-05 | Chạy với `ddl-auto=validate` không lỗi |
| - [x] RV-07 | BE | `ErrorCode.REVIEW_HIDDEN` (409, "Đánh giá đã bị ẩn, bạn chỉ có thể xoá.") + cập nhật `ErrorCodeTest` nếu có kiểm tra danh sách; thêm thông điệp vào `frontend/src/lib/errorMessages.ts` | — | Test `ErrorCode` xanh |
| - [x] RV-08 | Test | `ReviewSchemaTest` (kiểu `PromotionSchemaTest`): rating 0/6; có cả 2 hoặc không có `product_id`/`combo_id`; trùng (user, sản phẩm) và (user, combo); `is_hidden = true` thiếu lý do; `moderation_status` lạ — đều bị DB chặn | RV-06 | IT xanh |

### Phần 2 — Kiểm duyệt bằng từ điển

| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] RV-09 | BE | `TextNormalizer` (thuần hàm): NFC + bỏ ký tự vô hình + chữ thường; gộp chữ cái đơn bị chèn ký tự; leetspeak chỉ trên token lẫn chữ–số; gộp chữ lặp ≥ 3; hàm bỏ dấu (`đ→d`); tách token theo ranh giới từ + `TextNormalizerTest` (từng bước, có ca gõ dấu tổ hợp, zero-width, "1 lon" giữ số) | — | Unit test xanh |
| - [x] RV-10 | BE | `ContentModerator`, `ModerationResult`, `DictionaryModerator`: nạp `moderation/vi-banned-words.txt` lúc khởi động (`[accented]`/`[plain]`/`[abbr]`/`[allow]`), lỗi định dạng (mục ngoài section, section lạ, file thiếu) → ném lỗi khi khởi động; so khớp theo thuật toán 7 bước; `flaggedTerms` không trùng, cắt ≤ 500 ký tự | RV-09 | Test nạp file hỏng → lỗi; file mẫu nhỏ trong `src/test/resources` → khớp đúng từng section |
| - [x] RV-11 | BE | **Từ điển chính thức** `vi-banned-words.txt`: đủ các nhóm (chửi thề/thô tục; tình dục theo nghĩa tục; lăng mạ nhân phẩm/trí tuệ; kỳ thị vùng miền, dân tộc, tôn giáo, giới, xu hướng tính dục, khuyết tật; đe doạ bạo lực), mỗi nhóm có bản có dấu / không dấu / teencode / biến thể lách; `[allow]` cho cụm hay bị bắt nhầm; đầu file ghi nguồn tham khảo + quy tắc thêm mục. `DictionaryModeratorTest` tham số hoá: ≥ 40 câu **phải bắt**, ≥ 40 câu **không được bắt** (chê bai sạch, từ trùng khi bỏ dấu, viết tắt kỹ thuật) | RV-10 | ≥ 80 ca xanh; **người dùng xem từ điển** (điểm dừng 1) |

### Phần 3 — API khách + công khai (Roadmap N4-01)

| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] RV-12 | BE | `ReviewRepository`: danh sách công khai theo `product_id`/`combo_id` + `rating` tuỳ chọn, bỏ ẩn, `created_at DESC, id DESC`, phân trang; đếm theo sao (1 query `GROUP BY rating`); tìm của user cho 1 mặt hàng; tìm theo `id` + `user_id`; `findReviewIds(userId, productIds, comboIds)` | RV-06 | Biên dịch được; dùng ở RV-14…16 |
| - [x] RV-13 | BE | `order`: `OrderRepository.existsCompletedPurchase(userId, productId)` / `(userId, comboId)` (`EXISTS` trên `order_items` ⨝ `orders`, `status = COMPLETED`) + `OrderQueryService.hasCompletedPurchase(userId, ItemType, itemId)` | — | IT nhỏ: COMPLETED → true; PENDING/SHIPPING/CANCELLED/RETURNED → false; mua combo ≠ mua sản phẩm con |
| - [x] RV-14 | BE | `ReviewRequests` (`Create` có `@AssertTrue` đúng 1 trong `productId`/`comboId`, `@Min(1) @Max(5)`, `@Size(max = 2000)`; `Update`), DTO `ReviewResponse`, `MyReviewResponse`, `ReviewSummaryResponse` | RV-06 | Biên dịch được; thông báo validate tiếng Việt |
| - [x] RV-15 | BE | `ReviewService`: `create` (mặt hàng tồn tại + đang bán → `hasCompletedPurchase` → chưa có đánh giá → kiểm duyệt → lưu; bắt `DataIntegrityViolationException` → `REVIEW_ALREADY_EXISTS`), `update` (của chính mình, đang ẩn → `REVIEW_HIDDEN`, kiểm duyệt lại), `delete` (của chính mình, cả khi đang ẩn); nội dung trắng → `null` | RV-11, RV-12, RV-13, RV-14 | Biên dịch được |
| - [x] RV-16 | BE | `ReviewQueryService`: `list(itemType, itemId, rating, page, limit)`, `summary(...)` (TB làm tròn 1 chữ số, `ratingCounts` đủ 5 khoá kể cả 0), `mine(userId, itemType, itemId)`; mặt hàng không tồn tại/tắt → `NOT_FOUND` | RV-12, RV-14 | Biên dịch được |
| - [x] RV-17 | BE | `ReviewController`: `POST/PUT/DELETE /reviews`, `GET /reviews/mine`, `GET /products/{id}/reviews[/summary]`, `GET /combos/{id}/reviews[/summary]`; `SecurityConfig` bỏ `/api/users/me/reviews` (GET công khai đã phủ `/api/products/**`, `/api/combos/**`) | RV-15, RV-16 | Swagger hiện đủ endpoint |
| - [x] RV-18 | Test | `ReviewFlowIntegrationTest`: các ca ở "Cách test" (viết/không đủ điều kiện/trùng/xoá-viết lại/của người khác/validate/làm mờ-bỏ mờ/danh sách-lọc-phân trang/summary/`mine`/ẩn không hiện) + security (GET không token OK; POST không token 401; token Employee 403) | RV-17 | IT xanh |

### Phần 4 — Tích hợp hệ thống (Roadmap N4-01 — `ReviewSummary` cho catalog)

| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] RV-19 | BE | `Product` + `ProductCombo`: `@Formula averageRating` (`ROUND(AVG(rating), 1)`, bỏ ẩn) và `reviewCount` (bỏ ẩn); thay `null, 0` ở `ItemSummary`, `ProductDetailResponse`, `ComboSummary`, `ComboDetailResponse`, `CartService` (2 chỗ); sửa Javadoc "Đánh giá chưa có…" | RV-05 | IT: điểm/số lượt đúng ở `GET /products`, `GET /products/{slug}`, `GET /combos`, `GET /combos/{slug}`, `GET /cart`; đánh giá ẩn không tính; chưa có → `null`/0 |
| - [x] RV-20 | BE | Chi tiết đơn: `OrderDetailResponse.Item` thêm `myReviewId`; `OrderQueryService.getForCustomer` lấy id đánh giá của user cho mọi dòng **bằng 1 truy vấn** (`findReviewIds`), `canReview = COMPLETED && myReviewId == null`; sửa Javadoc "canReview = false" | RV-12 | IT: đơn 2 sản phẩm + 1 combo, đánh giá 1 dòng → đúng `myReviewId`/`canReview` từng dòng; đơn chưa COMPLETED → `canReview = false` |
| - [x] RV-21 | Chốt | `agent-lint.sh` + `agent-test.sh` backend; kiểm tra test cũ của catalog/combo/cart/order vẫn xanh | RV-18, RV-19, RV-20 | Lệnh chuẩn xanh (điểm dừng 2) |

### Phần 5 — API admin

| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] RV-22 | BE | `AdminReviewResponse`, `HideReviewRequest` (`@NotBlank @Size(max = 500)`), `AdminReviewService`: danh sách (`q` khớp họ tên/email khách hoặc tên mặt hàng, không dấu được như `/admin/products`; `itemType`, `rating`, `status` VISIBLE/HIDDEN/SENSITIVE; mới nhất trước; phân trang), `hide` (ghi `hidden_by` = người thao tác, đã ẩn → ghi đè lý do), `unhide`, `clearSensitive`, `delete` | RV-12 | Biên dịch được |
| - [x] RV-23 | BE | `AdminReviewController` `/admin/reviews`; `DELETE` có `@PreAuthorize("hasRole('ADMIN')")` | RV-22 | Swagger hiện đủ endpoint |
| - [x] RV-24 | Test | `AdminReviewIntegrationTest` (dùng `AdminMockMvc`): ẩn thiếu lý do → 400; ẩn → không còn ở danh sách công khai, `summary`/`@Formula` không tính; `mine` vẫn thấy kèm lý do; khách sửa → 409 `REVIEW_HIDDEN`, xoá → 200 rồi viết lại → 201; hiện lại; đánh dấu an toàn → `isSensitive = false`; Employee xoá → 403, Admin → 200; Customer gọi `/admin/reviews` → 403; lọc `q`/`status`/`rating`/`itemType` đúng | RV-23 | IT xanh (điểm dừng 3) |

### Phần 6 — Frontend khách

| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] RV-25 | FE | `types/api/review.ts`; `types/api/order.ts` thêm `myReviewId`; `services/reviewService.ts` (list, summary, mine, create, update, delete) | RV-01 | `tsc` sạch |
| - [x] RV-26 | FE | Thành phần nền trong `features/review/`: `StarRatingDisplay` (hỗ trợ `null` = sao trống), `StarRatingInput` (bàn phím + chuột, có nhãn trợ năng), `ReviewItem` (họ tên, ngày, sao, "(đã chỉnh sửa)", **làm mờ** + cảnh báo + nút Hiển thị khi `isSensitive`), `ReviewForm` (sao bắt buộc, ô nhận xét có bộ đếm /2000, chế độ tạo/sửa, hiện lỗi từ API) | RV-25 | Thử tay từng component; không vỡ ở 375px |
| - [x] RV-27 | FE | `ReviewSummaryBar` (TB + tổng + thanh phân bố bấm để lọc), `ReviewList` (lọc sao, "Xem thêm" 10/lần, loading/empty/error), `MyReviewBox` (đủ trạng thái: Guest → đăng nhập kèm `redirect`; chưa đủ điều kiện; form; đã viết + Sửa/Xoá có xác nhận; đang mờ; bị ẩn → chỉ Xoá; Employee/Admin chỉ xem), `ReviewSection` ghép 3 phần, tải lại summary + list sau khi gửi/sửa/xoá | RV-26 | Thử tay đủ 7 trạng thái người xem |
| - [x] RV-28 | FE | Trang sản phẩm: `ProductTabs` tab 4 dùng `ReviewSection`, **bỏ mock `localStorage`** (`cv-reviews:*`) và luật ≥ 16 ký tự; `ProductInfo` "Chưa có đánh giá" khi `reviewCount = 0`, link "Xem N đánh giá" cuộn tới tab; bỏ `Review` trong `types/shop.ts` + `reviews: []` ở `catalogService.ts` nếu không còn dùng | RV-27 | Thử tay sản phẩm có/không đánh giá |
| - [x] RV-29 | FE | Trang combo: thêm điểm ở đầu trang + `ReviewSection` (`itemType = COMBO`) trong `ComboPage` | RV-27 | Thử tay combo có/không đánh giá |
| - [x] RV-30 | FE | Sao thật trên thẻ: bỏ mặc định 5 sao (`?? 100`, `\|\| 100`, `rating: 100`) ở `ProductCard`, `ProductInfo`, `catalogService.ts` (3 chỗ), kiểm tra `Compare`, `LandingView`, `PdpDyn` | RV-25 | Thử tay danh sách, tìm kiếm, trang chủ, khuyến mãi, so sánh: chưa có đánh giá → sao trống |
| - [x] RV-31 | FE | Chi tiết đơn: `MyOrderDetail` thêm nút theo từng dòng (chỉ đơn COMPLETED: "Viết đánh giá" / "Xem đánh giá"); `ReviewDialog` (ảnh + tên mặt hàng, chế độ xem/tạo/sửa, bị ẩn → chỉ Xoá; `REVIEW_ALREADY_EXISTS` → gọi `mine` và chuyển sang sửa); cập nhật nút tại chỗ sau khi gửi/xoá | RV-26 | Thử tay đơn nhiều dòng (sản phẩm + combo); đơn chưa COMPLETED không có nút (điểm dừng 4) |

> Ghi chú Phần 6 (2026-11-06): thêm `GET /reviews/eligibility` → `{canReview}` (backend + test + api.md 4.6) vì trang sản
> phẩm/combo cần phân biệt "khách chưa đủ điều kiện" với "được viết" — kế hoạch ban đầu thiếu nguồn dữ liệu này.
> `MyReviewPanel` dùng chung cho trang sản phẩm/combo và `ReviewDialog`; danh sách nằm trong `ReviewSection` (không tách
> `ReviewList`). `.core` (sao trên thẻ) thêm nền 5 sao xám để "chưa có đánh giá" hiện sao trống. CSS: `styles/shop/review.css`.

### Phần 7 — Frontend admin

| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] RV-32 | FE | `services/admin/adminReviewService.ts`; `lib/admin/nav.ts` thêm mục "Đánh giá" (`/admin/reviews`, STAFF) | RV-23, RV-25 | `tsc` sạch; menu hiện cho Employee + Admin |
| - [x] RV-33 | FE | `/admin/reviews`: `ReviewListView` (bảng kiểu `UserListView`: mặt hàng có link, khách, sao, nội dung rút gọn + xem đủ, từ bị bắt, trạng thái, ngày; lọc `q`/loại/sao/trạng thái; phân trang), `HideReviewModal` (lý do bắt buộc ≤ 500); nút Hiện lại / Đánh dấu an toàn (chỉ khi `SENSITIVE`) / Xoá (chỉ Admin, có xác nhận) | RV-32 | Thử tay bằng tài khoản Employee và Admin (điểm dừng 5) |

> Ghi chú Phần 7 (2026-11-06): thao tác mỗi dòng gom vào menu "⋯" (`Dropdown`, như `ComboListView`) thay cho hàng nút.

### Hoàn tất

| ID | Loại | Task | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| - [x] RV-34 | Chốt | `agent-lint.sh` + `agent-test.sh` toàn bộ; kiểm tra UI (responsive, loading/empty/error); người dùng thử tay end-to-end: đặt đơn → COMPLETED → đánh giá từ chi tiết đơn → thấy ở trang sản phẩm → viết từ tục → bị mờ → admin ẩn → khách chỉ xoá được | Tất cả | Người dùng xác nhận; lint/test xanh |
| - [x] RV-35 | Docs | Rà docs theo code thật (`documentation-sync`), cập nhật trạng thái `pages.md`, mục mới trong `.agents/progress.md`, tick F-09 (phần reviews) + N4-01 trong `backend-roadmap.md`, kiểm lại V8 trên `origin`, chuyển plan sang `completed/` | RV-34 | Plan + docs khớp những gì đã làm |

Mở PR là việc của người dùng — agent không tự push/mở PR.
