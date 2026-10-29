# Danh sách tính năng (Feature Requirements)

Nguồn: báo cáo Phase1_v4 (5 phân hệ, bảng 2.1–2.11). Tên use case giữ nguyên tiếng Anh như báo
cáo để đối chiếu. Ánh xạ chi tiết use case → API → route → bảng: `use-cases.md`. Quy tắc nghiệp
vụ (BR-xx): `docs/02-ky-thuat/database.md` mục 4.

## Tier (ưu tiên trong 12 tuần — báo cáo không chia tier, xem decisions D-13)
- **Tier 1 — bắt buộc, làm trước:** luồng bán hàng end-to-end + quản trị cốt lõi.
- **Tier 2 — làm song song từ tuần 3:** các tính năng còn lại của báo cáo.
- **Tier 3 — chưa làm:** MoMo, chatbot AI. KHÔNG code nếu chưa được yêu cầu rõ ràng.

Lưu ý thiết kế xuyên tier:
- Giỏ hàng/đơn hàng (FR-09, FR-10) phải hỗ trợ combo ngay từ đầu (cột `combo_id`), dù quản lý
  combo (FR-15) thuộc Tier 2.
- Gửi email (SMTP) cần có từ Tier 1 vì OTP (FR-01, FR-03) dùng tới.
- WebSocket (FR-22, FR-26) dùng chung một hạ tầng.

## Phân hệ 1 — Xác thực và thông tin người dùng

| Mã | Yêu cầu | Tier | Actor | Use case (báo cáo) |
|---|---|---|---|---|
| FR-01 | Đăng ký bằng email + mật khẩu + họ tên; xác thực email bằng OTP; gửi lại mã | 1 | Guest | Authenticate User, Register, Type individual information, Authenticate email |
| FR-02 | Đăng nhập (email + mật khẩu, trả JWT), đăng xuất | 1 | Guest / User | Login, Logout |
| FR-03 | Quên mật khẩu: nhận OTP qua email, nhập mã + mật khẩu mới | 1 | Guest | Reset Password, Type new password |
| FR-04 | Xem/cập nhật hồ sơ (họ tên, SĐT, địa chỉ mặc định); đổi mật khẩu | 1 | User | View individual profile, Update Profile, Change Password |

## Phân hệ 2 — Quản trị hệ thống

| Mã | Yêu cầu | Tier | Actor | Use case (báo cáo) |
|---|---|---|---|---|
| FR-05 | Danh mục đa cấp: xem cây, tạo (chọn cha), sửa, xoá (chặn nếu còn con/hàng) | 1 | Admin | Manage categories, View categories list, Create/Update/Delete category |
| FR-06 | Sản phẩm: danh sách, chi tiết, tạo (kèm nhiều ảnh), sửa, ẩn/hiện (`is_active`) | 1 | Admin | Manage products, View product list/detail, Create/Update product |
| FR-08 | Tồn kho sản phẩm + combo: xem stock/reserved/available, điều chỉnh nhập/xuất có lý do, xem nhật ký | 1 | Admin, Employee | Manage Inventory, View stock levels, Adjust stock |
| FR-13 | Đơn hàng toàn hệ thống: danh sách, lọc theo trạng thái, chi tiết + lịch sử trạng thái, cập nhật trạng thái (xác nhận COD, giao hàng, hoàn tất) | 1 | Admin, Employee | Manage orders, View all orders, Filter orders by status, View order detail, Update order status |
| FR-14 | Người dùng: danh sách, tìm theo email/họ tên, chi tiết, sửa thông tin/role/khoá | 1 | Admin | Manage users, View user list, Search users, View user detail, Update user |
| FR-15 | Combo: danh sách, chi tiết, tạo (chọn sản phẩm thành phần + số lượng, giá combo, ảnh, danh mục), sửa, ẩn/hiện; tồn kho riêng | 2 | Admin | Manage combos, View combo list/detail, Create/Update combo |
| FR-16 | Voucher: danh sách, thêm, sửa, xoá (chỉ khi chưa dùng; đã dùng thì tắt `is_active`) | 2 | Admin | Manage promotions, View voucher list, Add/Update/Delete voucher |
| FR-17 | Chiến dịch giảm giá theo sản phẩm, có thời hạn; gán sản phẩm; trang chiến dịch | 2 | Admin | View discount campaigns, Add campaign, Update campaign |
| FR-19b | Xử lý yêu cầu trả hàng: duyệt / từ chối / xác nhận đã nhận hàng | 2 | Admin, Employee | Process return request (bổ sung, xem D-07) |
| FR-21b | Quản lý bài viết: danh sách, tạo, sửa, xoá, xuất bản | 2 | Admin | Manage article |
| FR-24 | Dashboard: doanh thu và số đơn theo khoảng ngày / theo tháng / theo năm; top sản phẩm bán chạy | 2 | Admin | View analytics dashboard, Filter by date range/month/year |
| FR-26b | Trả lời chat hỗ trợ | 2 | Admin, Employee | Exchange support messages |

## Phân hệ 3 — Mua hàng

| Mã | Yêu cầu | Tier | Actor | Use case (báo cáo) |
|---|---|---|---|---|
| FR-09 | Giỏ hàng: thêm sản phẩm/combo, sửa số lượng, xoá; luôn hiển thị giá hiện hành và số lượng khả dụng | 1 | Customer | Manage cart, Add to cart, Select a item, Update cart item, Remove cart item |
| FR-10 | Đặt hàng từ giỏ: nhập thông tin người nhận (tự điền từ hồ sơ), chọn phương thức thanh toán, áp voucher; giữ chỗ tồn kho có khoá | 1 | Customer | Place order, Select shipping address, Choose payment method, Apply promotion code |
| FR-11 | Thanh toán VNPay (IPN idempotent) hoặc COD | 1 | Customer, System | Pay order |
| FR-12 | Đơn cá nhân: lịch sử, chi tiết, huỷ khi PENDING | 1 | Customer | Manage individual orders, View orders history, View order detail, Cancel order |
| FR-18 | Đánh giá: viết (đã mua, đơn COMPLETED), sửa, xoá, xem danh sách đánh giá của mình | 2 | Customer | Write review, Update review, Manage review |
| FR-19a | Gửi yêu cầu trả hàng cho đơn COMPLETED trong thời hạn | 2 | Customer | Return order |

## Phân hệ 4 — Khám phá và hỗ trợ

| Mã | Yêu cầu | Tier | Actor | Use case (báo cáo) |
|---|---|---|---|---|
| FR-07 | Trang chủ; tìm kiếm theo từ khoá (sản phẩm, combo, bài viết); xem danh sách/chi tiết sản phẩm, combo; lọc theo danh mục, khoảng giá; sắp xếp theo giá/mới nhất | 1 | Guest+ | View homepage, Search, View result products/combos/articles, View products, View product detail, View combos, View combo detail, Filter by category, Sort |
| FR-20 | Yêu thích sản phẩm, combo, bài viết: thêm, xem, bỏ | 2 | Customer | Manage favorites, Add to favorite, View individual favorite, Remove favorite |
| FR-21a | Xem danh sách/chi tiết bài viết (blog, journal, guide) | 2 | Guest+ | View articles, View article detail |
| FR-26a | Nhắn tin hỗ trợ realtime với nhân viên | 2 | Customer | Exchange support messages |

## Phân hệ 5 — Hệ thống (tác nhân System)

| Mã | Yêu cầu | Tier | Use case (báo cáo) |
|---|---|---|---|
| FR-22 | Thông báo in-app realtime; xem, lọc đã đọc/chưa đọc, đánh dấu tất cả đã đọc | 2 | Push realtime notification, View notification, Filter by status, Mark as read all |
| FR-23 | Email tự động: OTP (Tier 1, thuộc FR-01/03), xác nhận đặt hàng, đổi trạng thái đơn, kết quả trả hàng | 2 | Send email |
| FR-25 | Gợi ý sản phẩm/combo mua kèm (co-purchase bằng SQL) | 2 | Gợi ý combo, sản phẩm |
| FR-26 | Kênh chat realtime (hạ tầng cho FR-26a/b) | 2 | Chat |
| — | Giữ chỗ / giải phóng tồn kho, huỷ đơn quá hạn | 1 | (System — thuộc FR-10, FR-11) |

## Tier 3 — chưa làm

| Mã | Yêu cầu | Ghi chú |
|---|---|---|
| FR-27 | Thanh toán MoMo | Thêm implementation `PaymentGateway` mới (xem `architecture.md`) |
| FR-28 | Chatbot AI | **Đang làm** (2026-10-25) — service riêng dùng chung DB (D-28), mô tả ở mục "FR-28 Chatbot AI" bên dưới; không đổi `support_messages` |

## Mô tả chi tiết từng FR

Mỗi FR: điều kiện trước → luồng chính → luồng ngoại lệ (kèm mã lỗi API). Quy tắc chi tiết
xem BR-xx trong `database.md`.

### FR-01 Đăng ký + xác thực email
- **Trước:** chưa đăng nhập.
- **Chính:** nhập email, mật khẩu (≥ 8 ký tự, có chữ và số), họ tên → tạo user `role=CUSTOMER`,
  `email_verified=false`, tạo giỏ hàng rỗng → gửi OTP (BR-11) → nhập mã → `email_verified=true`.
- **Ngoại lệ:** email đã tồn tại và đã xác thực → `EMAIL_ALREADY_EXISTS`; email tồn tại nhưng
  chưa xác thực → cập nhật mật khẩu/họ tên mới, gửi lại OTP (không báo lỗi); sai mã →
  `INVALID_OTP`; hết hạn → `OTP_EXPIRED`; sai quá 5 lần → `OTP_TOO_MANY_ATTEMPTS` (phải gửi lại
  mã); gửi lại quá sớm → `OTP_RESEND_TOO_SOON`.

### FR-02 Đăng nhập / đăng xuất
- **Chính:** email + mật khẩu đúng, `email_verified=true`, `is_active=true` → trả JWT + thông tin
  user. Đăng xuất → tăng `token_version`.
- **Ngoại lệ:** sai thông tin → `INVALID_CREDENTIALS` (không tiết lộ email có tồn tại hay không);
  chưa xác thực → `EMAIL_NOT_VERIFIED` (frontend chuyển sang bước nhập OTP, gọi gửi lại mã);
  bị khoá → `ACCOUNT_DISABLED`.

### FR-03 Quên / đặt lại mật khẩu
- **Chính:** nhập email → gửi OTP `RESET_PASSWORD` → nhập mã + mật khẩu mới → cập nhật hash,
  tăng `token_version`, xoá OTP.
- **Ngoại lệ:** email không tồn tại → vẫn trả thành công (chống dò email), không gửi gì; lỗi
  OTP như FR-01.

### FR-04 Hồ sơ
- **Chính:** xem/sửa `full_name`, `phone`, `address` (email không sửa được). Đổi mật khẩu cần
  mật khẩu hiện tại; thành công → tăng `token_version` (phải đăng nhập lại).
- **Ngoại lệ:** sai mật khẩu hiện tại → `INVALID_CURRENT_PASSWORD`.

### FR-05 Danh mục
- **Chính:** cây danh mục không giới hạn cấp; `path` dạng `/1/5/12/`. Tạo/sửa với `parent_id`.
  Trang admin: cây (tìm không dấu, số sản phẩm gồm danh mục con) + form tên, slug, danh mục cha
  (chọn từng cấp).
- **Ngoại lệ:** cha không tồn tại → `PARENT_CATEGORY_NOT_FOUND`; chọn cha là chính nó hoặc con
  cháu của nó → `CIRCULAR_CATEGORY_DEPENDENCY`; slug trùng → `SLUG_ALREADY_EXISTS`; xoá khi còn
  con/sản phẩm/combo → `CATEGORY_NOT_EMPTY` (BR-13). Đổi cha → cập nhật `path` của cả cây con
  trong cùng transaction.

### FR-06 Sản phẩm
- **Chính:** tạo/sửa tên, slug (tự sinh từ tên nếu trống), mô tả, thương hiệu, giá, danh mục, ảnh
  (upload Cloudinary, 1 ảnh chính, có thứ tự), mô tả ngắn, thông số (danh sách
  Tên–Giá trị admin tự nhập, có thứ tự). Không có biến thể (D-24): mỗi
  màu/size cần bán riêng là một sản phẩm riêng. Tồn kho ban đầu nhập qua FR-08 (không sửa
  `stock_quantity` trực tiếp ở form sản phẩm). Không xoá cứng — ẩn bằng `is_active=false`.
- **Ngoại lệ:** slug trùng → `SLUG_ALREADY_EXISTS`; giá ≤ 0 → `VALIDATION_ERROR`; tên thông số trùng
  → `VALIDATION_ERROR` (`fieldErrors.specs`).
- Sản phẩm `is_active=false`: không hiện ở trang public, không thêm vào giỏ được
  (`PRODUCT_INACTIVE`), đơn cũ vẫn hiển thị nhờ snapshot.

### FR-07 Khám phá / tìm kiếm
- **Chính:** chỉ hiện mặt hàng `is_active=true`, bài viết `is_published=true`. Lọc theo danh mục
  bao gồm danh mục con (`path LIKE '/1/5/%'`). Sắp xếp `price_asc`, `price_desc`, `newest`. Giá
  hiển thị là giá sau chiến dịch (BR-6) kèm giá gốc. Tìm kiếm không phân biệt hoa thường, theo
  tên (và tiêu đề bài viết).
- **Ngoại lệ:** slug không tồn tại hoặc đã ẩn → 404 `NOT_FOUND`.

### FR-08 Tồn kho
- **Chính:** danh sách sản phẩm + combo với `stock`, `reserved`, `available`, cảnh báo khi
  `available ≤ app.inventory.low-stock-threshold` (mặc định 5). Điều chỉnh: chọn mặt hàng,
  `change_quantity` (+ nhập / − xuất), lý do (IMPORT/EXPORT/ADJUST), ghi chú → ghi
  `inventory_logs` (BR-8).
- **Ngoại lệ:** `change_quantity = 0` hoặc làm `stock < reserved` → `INVALID_STOCK_ADJUSTMENT`.

### FR-09 Giỏ hàng
- **Chính:** mỗi Customer có 1 giỏ. Thêm mặt hàng đã có trong giỏ → cộng dồn số lượng. Giá luôn
  là giá hiện hành (D-04). `GET /cart` trả kèm `availableQuantity` và cờ `isActive` từng dòng.
- **Ngoại lệ:** vượt `available` (tính cả số đã có trong giỏ) → `OUT_OF_STOCK`; mặt hàng đã ẩn →
  `PRODUCT_INACTIVE`; số lượng ≤ 0 → `VALIDATION_ERROR`.

### FR-10 Đặt hàng
- **Trước:** giỏ không rỗng, đã đăng nhập.
- **Chính:** nhập người nhận/SĐT/địa chỉ (tự điền từ hồ sơ, tuỳ chọn "lưu làm mặc định"), chọn
  `VNPAY` hoặc `COD`, nhập voucher (tuỳ chọn) → server tính giá (BR-4, BR-5, BR-6, D-17), so với
  `expectedTotal`, khoá và giữ chỗ tồn kho (BR-2, mục 6 `database.md`), trừ lượt voucher, tạo
  đơn `PENDING` + lịch sử, xoá các dòng đã đặt khỏi giỏ. VNPay → trả `paymentUrl`; COD → chờ nhân
  viên xác nhận.
- **Ngoại lệ:** `CART_EMPTY`; `OUT_OF_STOCK` (kèm tên mặt hàng); `PRODUCT_INACTIVE`;
  `PRICE_CHANGED` (kèm tổng mới); voucher: `INVALID_VOUCHER`, `VOUCHER_EXPIRED`,
  `VOUCHER_MIN_ORDER_NOT_MET`, `VOUCHER_EXHAUSTED`, `VOUCHER_ALREADY_USED`; tạo URL VNPay lỗi →
  `PAYMENT_GATEWAY_ERROR` (rollback toàn bộ, không tạo đơn).

### FR-11 Thanh toán
- **VNPay:** khách được chuyển sang cổng; VNPay gọi IPN → xác minh (BR-12) → thành công: đơn
  `CONFIRMED`, trừ tồn kho thật; thất bại: `PAYMENT_FAILED`, giải phóng giữ chỗ, trả lượt
  voucher. Trang return chỉ hiển thị kết quả (đọc trạng thái đơn). Đơn VNPay PENDING quá 15 phút
  → huỷ tự động (BR-3).
- **COD:** đơn PENDING đến khi Employee/Admin xác nhận → `CONFIRMED`. Không timeout.
- Thanh toán lại cho đơn `PAYMENT_FAILED`: không hỗ trợ — khách đặt đơn mới.

### FR-12 Đơn cá nhân
- **Chính:** danh sách đơn của chính mình (lọc theo trạng thái, phân trang), chi tiết gồm dòng
  hàng (snapshot), tiền, người nhận, lịch sử trạng thái, thanh toán, yêu cầu trả hàng (nếu có).
  Huỷ khi `PENDING` → `CANCELLED`, giải phóng giữ chỗ, trả lượt voucher.
- **Ngoại lệ:** đơn của người khác → 404 `NOT_FOUND` (không tiết lộ tồn tại); huỷ khi không
  PENDING → `ORDER_NOT_CANCELLABLE`.

### FR-13 Xử lý đơn (Employee/Admin)
- **Chính:** chuyển trạng thái theo máy trạng thái (`database.md` mục 5), kèm ghi chú (vd. mã vận
  đơn khi SHIPPING). Xác nhận hoặc huỷ (bắt buộc ghi chú) đơn COD đang PENDING. Mỗi lần đổi ghi
  `order_status_history`, phát sự kiện thông báo/email.
- **Ngoại lệ:** chuyển sai máy trạng thái → `INVALID_STATUS_TRANSITION`; xác nhận đơn VNPay chưa
  thanh toán → `INVALID_STATUS_TRANSITION`.

### FR-14 Quản lý người dùng
- **Chính:** tìm theo email/họ tên, lọc theo role. Sửa `full_name`, `phone`, `role`, `is_active`.
  Khoá tài khoản hoặc đổi role → tăng `token_version`.
- **Ngoại lệ:** Admin tự khoá/tự hạ quyền chính mình → `CANNOT_MODIFY_SELF`.

### FR-15 Combo
- **Chính:** combo có tên, slug, mô tả, danh mục (chỉ cấp 1 — D-23), `combo_price`, danh sách sản phẩm thành
  phần (≥ 2 sản phẩm khác nhau) + số lượng trong một bộ. Ảnh combo = ảnh tự tải lên + ảnh chính của các sản phẩm thành phần (D-26). Tồn kho combo theo bộ (D-03):
  admin **đóng gói** N bộ (trừ kho từng sản phẩm thành phần) hoặc **tháo** (trả kho) — D-22. Form hiện tổng giá lẻ
  và mức tiết kiệm so với mua lẻ.
- **Ngoại lệ:** sản phẩm thành phần không tồn tại → `NOT_FOUND`; ít hơn 2 thành phần →
  `VALIDATION_ERROR`.

### FR-16 Voucher
- **Chính:** mã (in hoa, duy nhất), loại `PERCENTAGE`/`FIXED_AMOUNT`, giá trị, giảm tối đa (cho
  PERCENTAGE), đơn tối thiểu, số lượng phát hành, ngày bắt đầu/hết hạn, bật/tắt. Áp dụng theo BR-5.
- **Ngoại lệ:** xoá voucher đã có đơn sử dụng → `VOUCHER_IN_USE` (chỉ được tắt); `quantity` nhỏ
  hơn `used_count` → `VALIDATION_ERROR`.

### FR-17 Chiến dịch giảm giá
- **Chính:** tên, slug, mô tả, banner, loại/giá trị giảm, thời gian bắt đầu/kết thúc, bật/tắt,
  danh sách sản phẩm áp dụng. Giá hiển thị và giá đặt hàng theo BR-6.
- **Ngoại lệ:** `end_date ≤ start_date` → `VALIDATION_ERROR`.

### FR-18 Đánh giá
- **Chính:** rating 1–5 + nội dung (tuỳ chọn) cho sản phẩm hoặc combo đã mua (BR-10). Sửa/xoá
  đánh giá của mình. Trang chi tiết mặt hàng hiển thị điểm trung bình + số lượt + danh sách.
- **Ngoại lệ:** chưa mua → `NOT_PURCHASED`; đã có đánh giá → `REVIEW_ALREADY_EXISTS`; sửa/xoá
  đánh giá của người khác → 404 `NOT_FOUND`.

### FR-19 Trả hàng
- **Khách:** chọn đơn COMPLETED → nhập lý do → tạo yêu cầu `PENDING` (BR-9).
- **Nhân viên:** duyệt (`APPROVED`) hoặc từ chối (`REJECTED`, bắt buộc ghi chú); sau khi nhận
  hàng → `COMPLETED`: cộng tồn kho, ghi log, đơn `RETURNED`. Mỗi bước phát thông báo + email.
- **Ngoại lệ:** đơn không COMPLETED → `RETURN_NOT_ALLOWED`; quá hạn → `RETURN_WINDOW_EXPIRED`;
  đã có yêu cầu → `RETURN_ALREADY_EXISTS`; thao tác sai trạng thái yêu cầu →
  `INVALID_STATUS_TRANSITION`.

### FR-20 Yêu thích
- **Chính:** thêm/bỏ sản phẩm, combo, bài viết; xem danh sách theo loại. Thêm lại mục đã có →
  trả thành công (idempotent).

### FR-21 Bài viết
- **Chính:** tiêu đề, slug, tóm tắt, nội dung (HTML đã sanitize), ảnh đại diện, phân loại
  (`BLOG`/`JOURNAL`/`GUIDE`), xuất bản/nháp. Public chỉ thấy bài đã xuất bản.

### FR-22 Thông báo
- **Chính:** tạo khi: đơn đổi trạng thái, yêu cầu trả hàng đổi trạng thái, có tin nhắn hỗ trợ mới
  (cho khách khi nhân viên trả lời). Đẩy realtime qua WebSocket; lưu bảng `notifications`; danh
  sách lọc `isRead`; đánh dấu một/tất cả đã đọc; badge số chưa đọc.
- Nhân viên nhận thông báo khi: có đơn mới, có yêu cầu trả hàng mới (gửi tới mọi user role
  EMPLOYEE/ADMIN đang active).

### FR-23 Email
- OTP (FR-01/03), đặt hàng thành công, đơn đổi trạng thái (CONFIRMED, SHIPPING, COMPLETED,
  CANCELLED, PAYMENT_FAILED), kết quả trả hàng. Gửi bất đồng bộ (`@Async`) sau commit; lỗi gửi
  chỉ ghi log, không làm hỏng nghiệp vụ (trừ OTP: lỗi gửi → `EMAIL_SEND_FAILED`).

### FR-24 Dashboard
- **Chính:** doanh thu = tổng `final_amount` của đơn `COMPLETED` (không tính `RETURNED`) theo
  `orders.completed_at` (giờ Việt Nam, `Asia/Ho_Chi_Minh`). Gom nhóm theo
  ngày / tháng / năm; lọc khoảng ngày. Kèm số đơn theo trạng thái, top N mặt hàng theo số lượng
  bán (đơn COMPLETED).

### FR-25 Gợi ý mua kèm
- **Chính:** với mặt hàng X, trả tối đa 4 mặt hàng xuất hiện cùng X trong nhiều đơn
  `COMPLETED` nhất (co-purchase), chỉ mặt hàng đang `is_active` và còn hàng. Không đủ 4 → bù
  bằng mặt hàng bán chạy cùng danh mục.

### FR-26 Chat hỗ trợ
- **Khách:** một hội thoại duy nhất với bộ phận hỗ trợ; gửi/xem tin realtime.
- **Nhân viên:** danh sách hội thoại (sắp theo tin mới nhất), mở hội thoại một khách, trả lời.
  Tin của nhân viên lưu `user_id` = khách, `is_from_staff = true` (D-05).
- Nội dung 1–2000 ký tự, chỉ text.

### FR-28 Chatbot AI (Tier 3, D-28)
- **Ai dùng:** chỉ Customer đã đăng nhập. Admin quản lý tài liệu cho bot ở `/admin/chatbot/documents`.
- **Hỏi theo tài liệu:** bot tìm đoạn liên quan trong tài liệu đang bật và đã xử lý xong (`READY`), trả lời kèm nguồn
  (tên tài liệu, trang). Admin tắt/xoá/thay tài liệu thì từ câu hỏi sau bot dùng nội dung mới.
- **Hỏi về sản phẩm:** bot tự tra DB qua tool (theo tầm giá, danh mục, thương hiệu, còn hàng, so sánh) — không đoán giá;
  câu trả lời kèm thẻ sản phẩm/combo (ảnh, giá, link).
- **Tư vấn chuyến đi:** khách nêu số người, ngân sách, loại chuyến (cắm trại, leo núi trong ngày/qua đêm, đi biển…), số
  đêm. Bot chọn combo admin dựng khớp nhất (còn bộ), đánh giá ngay lúc hỏi từ thành phần + tên/mô tả combo; còn thiếu
  thì đề xuất thêm sản phẩm lẻ trong ngân sách còn lại; tổng tiền không vượt ngân sách. Không combo nào hợp thì ráp
  bằng sản phẩm lẻ và nói rõ.
- **Hội thoại:** khách có nhiều hội thoại, xem lại, xoá được; câu trả lời hiện dần (SSE).
- **Ngoại lệ:**
  - Chưa đăng nhập → widget mời đăng nhập; API trả 401. Role khác Customer → 403, widget không hiện.
  - Thiếu số người hoặc ngân sách khi xin tư vấn → bot hỏi lại, không tự đoán.
  - Không có tài liệu liên quan → bot nói chưa có thông tin, gợi ý liên hệ hỗ trợ (FR-26); không bịa.
  - Câu hỏi ngoài phạm vi đồ dã ngoại / cửa hàng → bot từ chối lịch sự.
  - Ngân sách thấp hơn mức tối thiểu cho đủ đồ bắt buộc → bot báo số tiền tối thiểu cần.
  - Gửi quá nhanh/quá nhiều → 429 `CHAT_RATE_LIMITED`; tin quá dài → 400 `VALIDATION_ERROR`.
  - Dịch vụ AI lỗi → sự kiện `error` `AI_UNAVAILABLE`, khách thử lại được; tin lỗi không được lưu.
  - Tài liệu PDF chỉ có ảnh (scan) → `FAILED` "Không trích được chữ".
