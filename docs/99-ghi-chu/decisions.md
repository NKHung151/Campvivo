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
