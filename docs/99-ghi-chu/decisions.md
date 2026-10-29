# Quyết định kỹ thuật (Architecture Decision Log)

Ghi lại mọi quyết định kỹ thuật/nghiệp vụ quan trọng để agent (và người) sau này không hỏi
lại hoặc vô tình làm ngược lại quyết định đã chốt. Quyết định mới đặt lên trên; quyết định cũ
không xoá, chỉ đánh dấu "Đã bị thay thế".

## Mẫu
### <Ngày> — <Tên quyết định>
- **Bối cảnh:**
- **Quyết định:**
- **Lý do:**
- **Phương án khác đã cân nhắc:**

---

## Đồng bộ theo báo cáo Phase1_v4 (2026-10-17)

### 2026-10-17 — D-00: Báo cáo Phase1 là nguồn yêu cầu gốc
- **Bối cảnh:** Báo cáo đồ án (Phase1_v4, chương 2: phân hệ, actor, use case, 22 bảng) được
  viết sau bộ docs ban đầu và mở rộng phạm vi đáng kể.
- **Quyết định:** Báo cáo Phase1 là nguồn yêu cầu gốc. `docs/` là bản kỹ thuật hoá của báo
  cáo: chi tiết hơn (cột, quy tắc, API) nhưng không được mâu thuẫn. Mọi chỗ docs bổ sung hoặc
  lệch so với báo cáo phải có mục trong file này. Ánh xạ use case → FR → API → route → bảng:
  `docs/03-san-pham/use-cases.md`.
- **Lý do:** Code phải khớp báo cáo để bảo vệ đồ án; agent cần một nguồn chi tiết đủ để sinh code.

### 2026-10-17 — D-01: Bỏ chức năng cho thuê
- **Quyết định:** Không làm cho thuê (rental). Xoá module `rental`, bảng/route Tier 3 cũ.
- **Lý do:** Báo cáo Phase1_v4 không còn chức năng này.
- Thay thế mục "Chọn bán là chính, thuê là phụ lục" bên dưới.

### 2026-10-17 — D-02: Không có sổ địa chỉ
- **Quyết định:** `users` có một địa chỉ mặc định (`phone`, `address`, nullable). Khi checkout,
  form tự điền từ hồ sơ, khách sửa được cho từng đơn; tuỳ chọn "lưu làm mặc định" cập nhật lại
  `users`. `orders` lưu snapshot người nhận (`recipient_name`, `recipient_phone`,
  `shipping_address`).
- **Lý do:** Báo cáo v4 đã bỏ use case "Manage address book".
- **Phương án khác:** bảng `user_addresses` — bị loại vì không có trong báo cáo.

### 2026-10-17 — D-03: Combo là mặt hàng riêng, có tồn kho riêng
- **Bổ sung bởi D-21 (2026-10-24):** tồn kho combo tăng/giảm bằng đóng gói/tháo, trừ/trả kho phân loại thành phần.
- **Quyết định:** `product_combos` có `stock_quantity`, `reserved_quantity` riêng. Combo được
  xem như bộ đóng gói sẵn, nhập kho theo bộ; tồn kho combo KHÔNG liên quan tồn kho sản phẩm
  con. `combo_items` chỉ dùng để hiển thị "combo gồm những gì", sửa thoải mái không ảnh hưởng
  đơn hàng.
- **Lý do:** Đơn giản hoá giữ chỗ/khoá tồn kho (không phải khoá nhiều sản phẩm con cho 1 combo).
- **Đánh đổi:** Nếu thực tế kho chỉ có hàng lẻ và ghép combo từ hàng lẻ thì mô hình này có thể
  bán vượt hàng thật. Chấp nhận vì giả định combo là bộ đóng gói sẵn.
- **Phương án khác:** gộp combo vào bảng `products` (`type = COMBO`) — gọn code hơn nhưng lệch
  báo cáo (báo cáo có ProductCombo riêng).

### 2026-10-17 — D-04: Giỏ hàng không chốt giá
- **Quyết định:** `cart_items` không có cột giá. Giỏ hàng luôn hiển thị giá hiện hành. Giá
  được chốt khi tạo đơn và lưu vào `order_items`. Client gửi `expectedTotal` khi đặt hàng;
  server tính lại, lệch thì trả `409 PRICE_CHANGED`.
- **Lý do:** Tránh bán theo giá cũ đã hết hiệu lực (chiến dịch hết hạn, admin sửa giá nhập
  nhầm); giỏ hàng không phải cam kết mua bán.
- Thay thế BR-4 cũ ("giá lấy theo `cart_items.price_at_add`"). Code hiện có
  `CartItem.priceAtAdd` phải bỏ khi viết lại module cart.

### 2026-10-17 — D-05: Chat hỗ trợ khách ↔ nhân viên; SupportMessage thêm `is_from_staff`
- **Quyết định:** Hiện chỉ làm chat giữa khách và Employee/Admin. Bảng `support_messages` giữ
  đúng các cột của báo cáo (`id`, `content`, `created_at`, `user_id`) và thêm đúng 1 cột
  `is_from_staff`. `user_id` LUÔN là khách hàng (chủ hội thoại); nhân viên trả lời thì ghi
  `user_id` = khách, `is_from_staff = true`.
- **Lý do:** 4 cột của báo cáo không đủ cho chat hai chiều (tin trả lời không biết thuộc khách
  nào). Thêm 1 cột là tối thiểu.
- **Hạn chế đã biết:** không lưu nhân viên nào trả lời; không có trạng thái đã đọc. Chatbot AI
  (giai đoạn sau) cần migration bổ sung (vd. đổi `is_from_staff` thành `sender_type`).
- **Phương án khác:** `receiver_id` — biết nhân viên nào trả lời, nhưng nhóm chọn phương án đơn
  giản hơn.

### 2026-10-17 — D-06: Yêu thích được cả bài viết
- **Quyết định:** `favorites` có `product_id`, `combo_id`, `article_id` (đúng 1 cột khác NULL).
- **Lý do:** Use case "Add to favorite" trong báo cáo gồm sản phẩm, combo, bài viết.

### 2026-10-17 — D-07: Có duyệt trả hàng; trả nguyên đơn; hoàn tiền thủ công
- **Quyết định:** Khách gửi yêu cầu trả hàng cho đơn COMPLETED trong thời hạn cấu hình (mặc
  định 7 ngày). Employee/Admin duyệt/từ chối, rồi xác nhận đã nhận hàng → cộng lại tồn kho, đơn
  chuyển `RETURNED`. Trả nguyên đơn, mỗi đơn tối đa 1 yêu cầu. Hoàn tiền xử lý ngoài hệ thống.
- **Lý do:** Báo cáo có ReturnRequest với "trạng thái xử lý" nhưng thiếu use case xử lý; trả
  từng phần và hoàn tiền tự động quá phạm vi.

### 2026-10-17 — D-08: Thanh toán VNPay + COD, MoMo để sau
- **Quyết định:** Tích hợp VNPay sandbox và COD. Thiết kế interface `PaymentGateway` để bổ sung
  MoMo sau mà không sửa `OrderService`.
- **Lý do:** Một cổng online trước để giảm rủi ro tiến độ.
- **Giả định:** vẫn giữ COD (cần nhóm xác nhận lại nếu muốn bỏ).

### 2026-10-17 — D-09: Review gộp rating + nội dung; khách xoá được
- **Quyết định:** Bảng `reviews` (báo cáo v4 gộp Rating + Comment). Mỗi (user, sản phẩm) và
  (user, combo) tối đa 1 review. Chỉ viết khi đã mua (đơn COMPLETED). Chủ review sửa được và xoá
  cứng được; xoá xong được viết lại.
- **Lý do:** Báo cáo không có `order_id` trong Review → kiểm tra "đã mua" bằng truy vấn đơn
  hàng; xoá cứng tránh vướng unique constraint.

### 2026-10-17 — D-10: OTP qua email cho xác thực email và đặt lại mật khẩu
- **Quyết định:** OTP 6 chữ số, lưu hash trên `users` (`otp_code_hash`, `otp_purpose`,
  `otp_expires_at`, `otp_attempts`, `otp_last_sent_at`). Không tạo bảng token riêng.
- **Lý do:** Báo cáo v4 chỉ định xác thực bằng mã; giữ đúng 22 bảng. Mỗi user chỉ có tối đa 1
  OTP hiệu lực tại một thời điểm (OTP mới ghi đè OTP cũ).

### 2026-10-17 — D-11: Đăng xuất bằng `token_version`
- **Quyết định:** JWT chứa `tokenVersion`. Logout (và đổi/đặt lại mật khẩu, khoá tài khoản) tăng
  `users.token_version` → mọi JWT cũ bị từ chối.
- **Đánh đổi:** đăng xuất là đăng xuất mọi thiết bị. Muốn đăng xuất từng thiết bị cần bảng
  refresh token (không làm).
- **Giả định:** nhóm chưa phản đối; đổi lại thì cập nhật mục này và `authentication.md`.

### 2026-10-17 — D-12: Schema vật lý PostgreSQL, giữ đúng 22 bảng
- **Quyết định:** Sơ đồ trong báo cáo là mức logic. Schema vật lý: tên bảng snake_case số
  nhiều (`users`, `orders` — tránh từ khoá `user`, `order`), cột snake_case, `BIGINT GENERATED
  ALWAYS AS IDENTITY`, `timestamptz`, `boolean`, tiền `NUMERIC(19,0)` (VND, Java `BigDecimal`,
  JSON số nguyên). Không thêm bảng ngoài 22 bảng của báo cáo; chỗ thiếu bổ sung bằng cột (danh
  sách ở `database.md` mục 2).
- Thay thế quy tắc cũ "tiền dùng `NUMERIC(12,2)`".

### 2026-10-17 — D-13: Giữ cơ chế Tier để xếp lịch
- **Quyết định:** Báo cáo không chia tier, docs vẫn chia Tier 1/2/3 (xem `features.md`) để ưu
  tiên trong 12 tuần. Tier 3 = MoMo, chatbot AI (chưa làm).

### 2026-10-17 — D-14: Viết lại migration V1/V2
- **Bối cảnh:** `V1__init_tier1.sql`, `V2__tier2.sql` chỉ là placeholder (comment), chưa chạy
  trên môi trường nào.
- **Quyết định:** Được thay bằng migration đầy đủ 22 bảng (một lần duy nhất, qua plan riêng).
  Từ sau đó áp dụng nghiêm quy tắc "không sửa migration đã merge".
- **Cập nhật 2026-10-23:** placeholder đã được thay bằng `V1__users.sql` (F-05). Các bảng còn lại thêm
  bằng migration riêng theo từng người (F-06 → F-09), không gộp 22 bảng một lần.

### 2026-10-17 — D-15: Realtime bằng WebSocket (STOMP)
- **Quyết định:** Thông báo realtime và chat hỗ trợ dùng chung Spring WebSocket + STOMP, xác
  thực JWT khi CONNECT. Side-effect (thông báo, email) chỉ chạy sau commit qua
  `@TransactionalEventListener(phase = AFTER_COMMIT)`.
- **Lý do:** Một hạ tầng cho cả hai tính năng; không gửi thông báo/email cho giao dịch rollback.
- **Phương án khác:** SSE cho thông báo — một chiều, không dùng được cho chat.

### 2026-10-17 — D-16: Brand và phân loại bài viết không có bảng riêng
- **Quyết định:** `products.brand` (varchar, nullable) phục vụ trang `/brands`;
  `articles.category` (BLOG / JOURNAL / GUIDE) phục vụ `/blog`, `/journal`, `/guides`;
  `discount_campaigns.slug` phục vụ `/promotions/[slug]`.
- **Lý do:** Frontend đã có các trang này; báo cáo không có bảng tương ứng; thêm cột rẻ hơn thêm bảng.

### 2026-10-17 — D-17: Phí vận chuyển cố định qua cấu hình
- **Quyết định:** `shipping_fee` = phí cố định (`app.shipping.fee`, mặc định 30000); miễn phí khi
  `subtotal − discount_amount ≥ app.shipping.free-threshold` (mặc định 1000000). Không tính theo
  vùng/khối lượng.
- **Giả định:** nhóm chưa chốt con số; chỉ đổi giá trị cấu hình, không đổi code.

### 2026-10-17 — D-18: Admin dùng Ant Design, tách root layout (store)/(admin)
- **Bối cảnh:** Storefront dùng CSS thuần chép từ trang tham khảo (`styles/shop/*`, có selector toàn
  cục `header`, `footer`). Trong App Router, CSS đã nạp không bị gỡ khi điều hướng phía client, nên
  admin dùng chung root layout sẽ bị dính style storefront. Admin có nhiều trang bảng/form.
- **Quyết định:**
  - Hai root layout bằng route group: `app/(store)/layout.tsx` (storefront, tài khoản, auth — nạp CSS
    storefront) và `app/(admin)/layout.tsx` (admin — Ant Design). Không còn `app/layout.tsx` chung.
    Chuyển giữa hai bên luôn tải lại toàn trang → CSS tách hẳn. URL không đổi.
  - Admin dùng **Ant Design 5** (`antd`, `@ant-design/icons`, `@ant-design/nextjs-registry`), locale
    `vi_VN`, màu chủ đạo `#2f6a3f`. Storefront giữ CSS hiện có, không dùng antd.
  - URL không khớp route nào → catch-all `(store)/[...notFound]` → trang 404 storefront. Trang admin có
    trong menu nhưng chưa làm → catch-all `(admin)/admin/[...slug]` hiển thị "đang xây dựng".
- **Lý do:** antd có sẵn Table, Form, Upload, TreeSelect, DatePicker — rút ngắn đáng kể thời gian làm
  ~12 trang admin; tách root layout là cách chuẩn của Next để cô lập CSS.
- **Phương án khác:** Tailwind + tự viết component (tốn công); app admin riêng (trùng types/services,
  thêm deploy và CORS).
- Thay thế dòng "CSS: Tailwind (admin, component mới)" cũ trong `tech-stack.md`.

### 2026-10-23 — D-19: Chưa seed dev bằng `V6__seed_dev.sql`; tạm chèn tay tài khoản mẫu
- **Bối cảnh:** Roadmap F-12 định seed bằng `V6__seed_dev.sql` (tài khoản, danh mục, sản phẩm, combo, voucher...). Hiện chỉ có
  `V1__users.sql`; các bảng khác thuộc F-06 → F-09 của người khác chưa vào `dev`. Cần tài khoản ADMIN/EMPLOYEE/CUSTOMER
  để thử đăng nhập và điều hướng theo role.
- **Quyết định:** Chưa tạo `V6`. Mỗi người tự chèn 3 tài khoản bằng SQL idempotent (`local-development.md`). F-12 làm sau khi
  F-06 → F-09 đã merge.
- **Lý do:** Chèn seed có phiên bản lớn (V6) trước khi V2–V5 tồn tại thì Flyway dự kiến báo lỗi *"Detected resolved migration
  not applied to database"* trên mọi DB đã chạy V6 (chưa tái hiện, suy ra từ hành vi mặc định `out-of-order=false`). Dữ liệu
  danh mục/sản phẩm cũng thuộc người khác.
- **Phương án khác (chưa thử):** migration lặp lại `R__seed_dev_*.sql` (Flyway luôn chạy sau mọi migration có phiên bản).
- **Cảnh báo triển khai:** `application.yml` đang đặt `spring.profiles.active: dev` mặc định. Khi làm seed chính thức phải
  chỉ nạp thư mục seed ở profile `dev` và bắt buộc đặt profile khi triển khai (`deployment.md`), kẻo tài khoản mẫu với mật
  khẩu công khai được tạo trên production.

### 2026-10-23 — D-20: Phân loại hàng một chiều, cùng giá, chưa dùng SKU; thông số dạng danh sách
- **Phần phân loại đã bị thay thế bởi D-24 (2026-10-24): bỏ biến thể.** Phần `specs` và
  `short_description` vẫn giữ.
- **Bối cảnh:** Dữ liệu mock (lấy làm dữ liệu thật) có ~140/400 sản phẩm nhiều màu/size, tồn kho
  riêng từng loại; trang chi tiết storefront có chọn phân loại và bảng thông số. Thay thế quyết định
  2025 "Không dùng biến thể".
- **Quyết định:**
  - Bảng `product_variants`; **mọi sản phẩm có ≥ 1 biến thể**. Sản phẩm đơn có 1 biến thể ẩn tên
    "Mặc định" (`products.variant_label` = NULL). Sản phẩm nhiều loại: `variant_label` (vd. "Màu sắc")
    + 1–50 biến thể, tên không trùng (không phân biệt hoa thường).
  - **Một chiều** (chỉ Màu *hoặc* Size, không tổ hợp Màu × Size).
  - **Giá ở sản phẩm**, mọi biến thể cùng giá. Không có SKU (chưa cần mã kho/barcode).
  - Tồn kho/giữ chỗ (BR-1, BR-2) ở **cấp biến thể**; tồn kho sản phẩm = tổng. `cart_items` và
    `order_items` có `variant_id`. Biến thể còn tồn/giữ chỗ không xoá được — chỉ tắt.
  - `products.specs` JSONB = danh sách `{name, value}` có thứ tự, cấu trúc giống nhau cho mọi loại
    hàng; admin tự nhập từng dòng, không có schema riêng từng danh mục.
  - Thêm `short_description`. (Ban đầu có thêm `warranty` — đã gỡ ngày 2026-10-24 vì không có trong thiết
    kế DB gốc và chưa được duyệt.)
- **Lý do:** Đủ để bán đúng dữ liệu đang có mà không phải làm ma trận biến thể/giá riêng/SKU.
- **Ảnh hưởng:** Người 1 (giỏ hàng, đơn hàng) khoá và trừ kho theo `ProductVariant`; báo cáo cần bổ
  sung bảng ProductVariant vào ERD.

### 2026-10-24 — D-21: Khung thông số khai báo theo danh mục
- **Đã bỏ (D-25, 2026-10-24).**
- **Bối cảnh:** D-20 để "khung theo danh mục" là gợi ý tự rút từ thông số của sản phẩm đã có. Admin không
  biết trước khung gồm gì, dữ liệu mock lẫn tên trùng nghĩa, danh mục mới thì khung rỗng.
- **Quyết định:** `categories.spec_template` (JSONB, mảng tên thông số, ≤ 30) do admin khai báo ở trang
  Danh mục. Danh mục không khai báo thì **thừa hưởng** khung của danh mục cha gần nhất
  (`GET /api/admin/categories/{id}/spec-template`). Form sản phẩm điền sẵn các dòng theo khung khi chọn danh
  mục; vẫn thêm được dòng ngoài khung. Cấu trúc `products.specs` không đổi. Gợi ý tự rút từ dữ liệu
  (`/spec-names`) chỉ còn dùng làm gợi ý khi gõ và nút "Lấy gợi ý từ sản phẩm" khi soạn khung.
- **Lý do:** giống "thuộc tính theo ngành hàng" của Shopee/Shopify nhưng gọn: chỉ tên thông số, không có
  kiểu dữ liệu, không bắt buộc.
- **Ảnh hưởng:** migration `V3__category_spec_template.sql`; báo cáo bổ sung thuộc tính specTemplate cho
  Category.

### 2026-10-24 — D-22: Đóng gói combo trừ kho sản phẩm thành phần
- **Bối cảnh:** D-03 cho combo tồn kho riêng nhưng không trừ kho sản phẩm con → có thể bán vượt hàng thật
  khi combo được ghép từ hàng lẻ.
- **Quyết định:** giữ D-03 (combo là mặt hàng riêng, tồn kho theo bộ; khách mua combo chỉ giữ chỗ/trừ kho
  combo — giỏ hàng, đơn hàng không đổi). Thêm thao tác admin:
  - **Đóng gói N bộ:** kiểm tra đủ hàng khả dụng, trừ `N × số lượng` ở từng sản phẩm thành phần và cộng N vào
    kho combo, trong một transaction (khoá combo rồi các sản phẩm theo id tăng dần). Thiếu → `OUT_OF_STOCK`.
  - **Tháo N bộ** (≤ số bộ chưa bị giữ chỗ): trả hàng về từng sản phẩm, trừ kho combo.
  - Combo còn bộ đã đóng gói thì không đổi được thành phần (tháo hết trước).
  - Chưa ghi `inventory_logs` (làm cùng module Tồn kho).
- **Ảnh hưởng:** không đổi cấu trúc bảng (`combo_items` giữ nguyên như báo cáo). Bản đầu gắn mỗi dòng với
  một phân loại (`combo_items.variant_id`, migration V4) — đã gỡ cùng D-24.

### 2026-10-24 — D-23: Combo chỉ gắn danh mục cấp 1
- **Bối cảnh:** combo có trang riêng (`/combos`), danh mục chỉ để lọc/nhóm; chọn tới cấp 3 như sản phẩm là thừa.
- **Quyết định:** giữ `product_combos.category_id` (không đổi DB) nhưng chỉ nhận danh mục gốc (`parent_id IS NULL`);
  danh mục con → 400 `fieldErrors.categoryId`. Form và bộ lọc combo chỉ liệt kê danh mục cấp 1.

### 2026-10-24 — D-24: Bỏ biến thể, quay về mỗi mặt hàng là một sản phẩm
- **Bối cảnh:** thiết kế gốc (quyết định 2025 "Không dùng biến thể", báo cáo Phase1) không có biến thể. D-20
  thêm phân loại một chiều để bán đúng dữ liệu mock, nhưng kéo theo đổi giỏ hàng, đơn hàng (của thành viên
  khác), combo theo phân loại, và còn phát sinh thêm (ảnh theo phân loại, tồn kho theo phân loại…).
- **Quyết định:** bỏ hẳn biến thể, áp dụng lại quyết định 2025:
  - Không có bảng `product_variants`, cột `products.variant_label`, `combo_items.variant_id`.
  - `products.stock_quantity`, `reserved_quantity` như thiết kế gốc; BR-1, BR-2 tính trên sản phẩm.
  - Màu/size cần bán và quản lý tồn riêng → tạo sản phẩm riêng (vd. "Áo mưa Coleman – Tím – S/M").
  - Giữ `specs`, `short_description` (D-20).
  - Giỏ hàng, đơn hàng trả về đúng mã gốc (theo `product_id`).
- **Dữ liệu:** seed V2 lấy tồn kho mỗi sản phẩm = tổng tồn các phân loại cũ. DB local được chuyển bằng SQL
  (không xoá dữ liệu), migration V1/V2/V5 sửa tại chỗ, V4 xoá.
- **Ảnh hưởng:** báo cáo không cần bổ sung bảng ProductVariant.

### 2026-10-24 — D-25: Bỏ khung thông số theo danh mục
- **Bối cảnh:** D-21 cho mỗi danh mục một khung tên thông số (kèm gợi ý rút từ sản phẩm đang có) để form sản
  phẩm điền sẵn. Thực tế thông số mỗi sản phẩm mỗi khác, admin phải duy trì khung ở trang Danh mục mà ít dùng.
- **Quyết định:** bỏ khung và gợi ý. Form sản phẩm chỉ có các dòng "Tên – Giá trị" để tự nhập (thêm/xoá/đổi thứ
  tự). Xoá cột `categories.spec_template` (migration V3 xoá; DB local bỏ cột, giữ dữ liệu khác) và hai API
  `GET /admin/categories/{id}/spec-template`, `/spec-names`.
- **Ảnh hưởng:** bảng `categories` về đúng thiết kế gốc; báo cáo không cần thuộc tính specTemplate.

### 2026-10-24 — D-26: Ảnh combo = ảnh tự tải lên + ảnh sản phẩm thành phần
- **Bối cảnh:** combo là bộ các sản phẩm đã có ảnh; bắt admin tải ảnh riêng cho mọi combo thì thừa, nhưng vẫn cần ảnh
  riêng khi muốn (ảnh chụp cả bộ). `images.image_url` là duy nhất toàn bảng nên không chép trùng URL ảnh sản phẩm
  sang combo được.
- **Quyết định:** combo vẫn có ảnh tự tải lên (`images.combo_id`, không bắt buộc, ≤ 10, có ảnh thì đúng 1 ảnh chính).
  Ảnh hiển thị = ảnh tự tải lên (ảnh chính trước) rồi ảnh chính của từng sản phẩm thành phần theo thứ tự dòng, bỏ trùng.
  Ảnh đại diện = ảnh chính tự tải lên, chưa có thì ảnh của sản phẩm thành phần đầu tiên có ảnh. Ảnh sản phẩm không
  lưu sang combo — đổi ảnh sản phẩm thì combo hiển thị theo.
- **Dữ liệu:** không đổi cấu trúc bảng. Seed V5 không còn chèn ảnh riêng cho combo (dùng ảnh sản phẩm).

### 2026-10-25 — D-27: Nâng Spring Boot 3.3.0 → 3.5.16; PostgreSQL dùng image có pgvector
- **Bối cảnh:** chatbot AI (FR-28, D-28) dùng Spring AI. Spring AI 1.x chạy trên Spring Boot 3.5; Spring AI 2.0 đòi
  Spring Boot 4 (Spring Framework 7, Jackson 3) — đổi lớn, không đáng cho đồ án. Embedding cần extension pgvector.
- **Quyết định:**
  - `spring-boot-starter-parent` 3.5.16; springdoc 2.8.17 (bản 2.6 làm Swagger lỗi `NoSuchMethodError` với
    Spring Framework 6.2). Không lên Boot 4.
  - PostgreSQL giữ bản 16, đổi image `postgres:16` → `pgvector/pgvector:pg16` (cùng PostgreSQL 16 + extension
    `vector`) ở compose, Testcontainers và CI. Volume dữ liệu cũ dùng tiếp được, không cần dump/restore.
- **Đã kiểm:** 123/123 test backend trên Boot 3.5.16 với `postgres:16`, `pgvector/pgvector:pg16` và `pg18`; chạy app
  với seed V1–V4: health UP, Swagger 200, API sản phẩm/combo đúng, `CREATE EXTENSION vector` chạy được. Đối chiếu
  thay đổi của Boot 3.4/3.5 (graceful shutdown mặc định, `@MockBean` deprecated, validate `@ConfigurationProperties`
  lồng nhau cần `@Valid`, chỉ còn bean `applicationTaskExecutor`…): code hiện có không bị ảnh hưởng.
- **Còn lại:** 4 cảnh báo deprecation `Specification.where(...)` (catalog, combo) — vẫn chạy; chủ module đổi sang
  `Specification.allOf(...)` khi tiện.

### 2026-10-25 — D-28: Chatbot AI (FR-28) là service riêng dùng chung DB, gộp vào backend sau
- **Bối cảnh:** FR-28 (Tier 3) được yêu cầu làm. Phần nghiệp vụ còn lại (giỏ, đơn, khuyến mãi) chưa xong; chatbot
  cần làm và demo được độc lập mà không chặn/đụng code của thành viên khác.
- **Quyết định:**
  - App Spring Boot riêng `chatbot-service/` (cổng 8090, package `vn.campvivo.chatbot`, cùng quy ước code và format
    lỗi với backend). Spring AI 1.1.x + OpenAI (chat + embedding `text-embedding-3-small`), pgvector.
  - **Dùng chung DB `campvivo`**, không có DB bản sao: chỉ **đọc** `users`, `categories`, `products`, `images`,
    `product_combos`, `combo_items`; chỉ ghi các bảng riêng tiền tố `chatbot_` (`database.md` 3.24, ngoài 22 bảng
    báo cáo). Lịch sử Flyway riêng `flyway_schema_history_chatbot`.
  - Dùng chung `JWT_SECRET`; filter của chatbot kiểm `is_active` + `token_version` trong `users` giống backend nên
    đăng xuất ở backend là token mất hiệu lực ở chatbot. Chỉ **CUSTOMER** chat; tài liệu do **ADMIN** quản lý.
  - LLM **không viết SQL**. LLM chỉ gọi tool có tham số định kiểu (tìm sản phẩm, xem combo, tư vấn chuyến đi, tìm
    tài liệu); tool chạy query cố định. Giá/tồn kho trong câu trả lời luôn lấy từ DB.
  - Tư vấn combo dùng **combo admin dựng trong DB**; mức phù hợp (số người, loại chuyến, mùa, món còn thiếu) được tính
    **mỗi lần khách hỏi** từ thành phần + tên/mô tả combo, không lưu bảng phụ. Thiếu thì bổ sung sản phẩm lẻ trong
    ngân sách còn lại; việc chọn món và cộng tiền làm bằng code Java, không để LLM tính.
  - Hội thoại bot lưu ở `chatbot_conversations`/`chatbot_messages`, tách khỏi `support_messages` (D-05 không đổi).
- **Lý do:** dùng chung DB thì giá, tồn kho, combo mới luôn đúng ngay, không phải đồng bộ; service riêng thì nhóm
  không phải chờ và chatbot không cần `OPENAI_API_KEY` để backend chạy.
- **Lộ trình gộp:** chuyển package vào `backend/`, migration chatbot thành `V<n>__chatbot.sql`, bỏ filter JWT chép
  lại, đọc catalog qua service public của `catalog`/`combo` (cần chủ module thêm hàm), dùng `PricingService` khi có.
- **Phương án khác:** module trong backend ngay (phải chờ/đụng code chung); DB bản sao (giá/tồn kho lệch, phải đồng
  bộ); text-to-SQL (rủi ro bảo mật, kết quả khó kiểm).

---

## Quyết định trước đó

### 2025 (đầu dự án) — Chọn "bán là chính, thuê là phụ lục"
- **Đã bị thay thế bởi D-01 (2026-10-17): bỏ cho thuê.**
- **Bối cảnh:** Ý tưởng ban đầu là web cho thuê đồ dã ngoại thuần túy; sau đó cân nhắc theo
  mô hình WeTrek (vừa bán vừa thuê, nhiều biến thể, tích hợp vận chuyển thật).
- **Quyết định:** Tier 1 = bán hàng đầy đủ vòng đời; Tier 2 = các tính năng nâng cao
  (combo, khuyến mãi, đánh giá, thông báo, dashboard, gợi ý); Tier 3 = cho thuê giới hạn,
  chỉ mở sau mốc tuần 9 nếu Tier 1+2 ổn định.
- **Lý do:** Nhóm 4 người, 3 tháng, ưu tiên an toàn tiến độ. Làm cả mua lẫn thuê đồng thời
  với đầy đủ biến thể sản phẩm và tích hợp vận chuyển thật là quá tải cho quy mô đồ án.
- **Phương án khác đã cân nhắc:** Clone toàn bộ mô hình WeTrek (bán + thuê + biến thể +
  vận chuyển thật) — bị loại vì khối lượng nghiệp vụ gấp nhiều lần, rủi ro vỡ tiến độ cao.

### 2025 — Không dùng biến thể sản phẩm (variants)
- **Bị D-20 (2026-10-23) thay thế, rồi được áp dụng lại bởi D-24 (2026-10-24).**
- **Bối cảnh:** Tài liệu tham khảo (mô hình Campvivo mở rộng) có `product_variants`,
  `rental_variant_pricing` cho màu/size/chất liệu.
- **Quyết định:** Không tạo bảng biến thể riêng. Mỗi sản phẩm (vd. "Lều 2 người") là 1 bản
  ghi `products` độc lập.
- **Lý do:** Đồ dã ngoại (lều, bếp, túi ngủ) không cần biến thể phức tạp như quần áo/giày;
  thêm biến thể chỉ tốn effort mà không tăng điểm kỹ thuật tương xứng.

### 2025 — Không tích hợp API vận chuyển thật
- **Quyết định:** Mô phỏng trạng thái vận đơn (nhân viên cập nhật thủ công
  CONFIRMED→SHIPPING→COMPLETED), không gọi API thật của đơn vị vận chuyển.
- **Lý do:** Tích hợp bên thứ ba rủi ro thời gian cao (cần đăng ký doanh nghiệp, chờ duyệt),
  không phải trọng tâm kỹ thuật của đồ án.
