# Danh sách trang (Routes)

Agent code frontend PHẢI dùng đúng route dưới đây — không tự đặt tên khác. Cột "API" là endpoint
nguồn dữ liệu (`docs/02-ky-thuat/api.md`). Trạng thái: ✅ đã có UI (dữ liệu mock), ⬜ chưa có.

## Public (Guest trở lên) — nhóm `src/app/(store)/(shop)/`
| Route | Trang | API | Trạng thái |
|---|---|---|---|
| `/` | Trang chủ: sản phẩm mới, combo, chiến dịch, bài viết | `/products`, `/combos`, `/articles` | ✅ |
| `/products` | Tất cả sản phẩm: lọc danh mục/thương hiệu/khoảng giá, sắp xếp, phân trang | `GET /products` | ✅ |
| `/products/[slug]` | Chi tiết sản phẩm: ảnh, giá (gốc + sau chiến dịch), tồn kho khả dụng, thêm giỏ, yêu thích, đánh giá, gợi ý mua kèm | `/products/{slug}`, `/products/{id}/reviews`, `/products/{id}/recommendations` | ✅ |
| `/categories/[slug]` | Sản phẩm theo danh mục (gồm danh mục con), bộ lọc, sắp xếp | `GET /products?category=` | ✅ |
| `/categories/san-pham-moi` | Hàng mới về — slug đặc biệt, không phải danh mục DB | `GET /products?sortBy=newest` | ✅ |
| `/combos` | Danh sách combo | `GET /combos` | ⬜ |
| `/combos/[slug]` | Chi tiết combo: thành phần, giá combo, tồn kho, thêm giỏ, yêu thích, đánh giá, gợi ý | `/combos/{slug}`, `/combos/{id}/reviews`, `/combos/{id}/recommendations` | ⬜ |
| `/brands`, `/brands/[slug]` | Thương hiệu (từ `products.brand`, slug = slugify(brand)) | `GET /brands`, `GET /products?brand=` | ✅ |
| `/search?q=&type=` | Kết quả tìm kiếm: tab Sản phẩm / Combo / Bài viết | `GET /search` | ✅ (thiếu tab Combo) |
| `/promotions/[slug]` | Trang chiến dịch giảm giá | `GET /campaigns/{slug}` | ✅ |
| `/blog`, `/blog/page/[n]`, `/blog/[slug]` | Bài viết `category = BLOG` | `GET /articles?category=BLOG`, `/articles/{slug}` | ✅ |
| `/journal`, `/journal/page/[n]`, `/journal/[slug]` | Outdoor Journal `category = JOURNAL` | `GET /articles?category=JOURNAL` | ✅ |
| `/guides` | Kiến thức outdoor `category = GUIDE` | `GET /articles?category=GUIDE` | ✅ |
| `/blog/category/[slug]`, `/journal/category/[slug]` | Danh mục con của bài viết | **Không có nguồn dữ liệu** — schema chỉ có `articles.category` (3 giá trị). Bỏ route khi nối API, trừ khi nhóm quyết định bổ sung | ✅ (mock) |
| `/pages/[slug]` | Trang tĩnh: giới thiệu, chính sách (đổi trả, vận chuyển, bảo mật) | Nội dung tĩnh trong frontend, không có API | ✅ |

## Auth — nhóm `src/app/(store)/(auth)/`
| Route | Trang | API | Trạng thái |
|---|---|---|---|
| `/login` | Đăng nhập (email + mật khẩu). Có thể mở dạng popup trên trang hiện tại, nhưng route là nguồn chuẩn; `?redirect=` để quay lại | `POST /auth/login` | ✅ (email + mật khẩu; sau đăng nhập về `?redirect=` nếu role được vào, không thì ADMIN → `/admin`, EMPLOYEE → `/admin/orders`, CUSTOMER → `/`; popup OTP giả đã bỏ). Chưa có liên kết tới `/register`, `/forgot-password` (trang chưa làm); `EMAIL_NOT_VERIFIED` mới chỉ hiện thông báo, chưa tự chuyển `/verify-email` |
| `/register` | Đăng ký | `POST /auth/register` | ⬜ |
| `/verify-email?email=` | Nhập OTP xác thực email, gửi lại mã (đếm ngược 60s) | `/auth/verify-email`, `/auth/resend-code` | ⬜ (tái sử dụng UI popup OTP) |
| `/forgot-password` | Nhập email nhận mã | `POST /auth/forgot-password` | ⬜ |
| `/reset-password?email=` | Nhập OTP + mật khẩu mới | `POST /auth/reset-password` | ⬜ |

## Customer (role CUSTOMER) — `src/app/(store)/(shop)/cart`, `src/app/(store)/account/`
| Route | Trang | API | Trạng thái |
|---|---|---|---|
| `/cart` | Giỏ hàng + thanh toán trên **một trang** (quyết định có chủ đích): danh sách dòng (giá hiện hành, cảnh báo hết hàng/ngừng bán), form người nhận (tự điền từ hồ sơ, tick "lưu làm mặc định"), voucher, phương thức VNPay/COD, tóm tắt tiền | `/cart`, `/orders/preview`, `/vouchers/validate`, `POST /orders` | ✅ (mock) |
| `/checkout` | Chuyển hướng về `/cart` | — | ✅ |
| `/checkout/success?orderId=` | Kết quả đặt hàng. COD: thành công. VNPay: trang return của VNPay trỏ về đây, gọi API return để hiển thị đúng trạng thái (thành công / thất bại / đang chờ) | `GET /payments/vnpay/return`, `GET /orders/{id}` | ✅ (mock) |
| `/account` | Hồ sơ: họ tên, SĐT, địa chỉ mặc định; đổi mật khẩu | `/users/me`, `/users/me/password` | ✅ (hiện là trang tab — tách thành route con) |
| `/account/orders` | Lịch sử đơn, lọc theo trạng thái | `GET /orders` | ✅ (tab) |
| `/account/orders/[id]` | Chi tiết đơn: dòng hàng, tiền, người nhận, lịch sử trạng thái; nút Huỷ (PENDING), Yêu cầu trả hàng (COMPLETED, trong hạn), Viết đánh giá từng dòng | `/orders/{id}`, `/orders/{id}/cancel`, `/orders/{id}/returns`, `POST /reviews` | ⬜ |
| `/account/reviews` | Đánh giá của tôi: sửa, xoá | `/users/me/reviews`, `PUT/DELETE /reviews/{id}` | ⬜ |
| `/account/favorites` | Yêu thích: tab Sản phẩm / Combo / Bài viết | `/users/me/favorites`, `DELETE /favorites/{id}` | ✅ (tab) |
| `/account/notifications` | Thông báo: lọc đã đọc/chưa đọc, đánh dấu tất cả đã đọc | `/notifications` | ⬜ |
| `/account/support` | Chat hỗ trợ với nhân viên (realtime) | `/support/messages`, WS `/user/queue/support` | ⬜ |

`/account/*` dùng chung layout có menu bên trái. Tab "địa chỉ" và "điểm" trong UI mock hiện tại bị
bỏ (không có sổ địa chỉ — D-02; không có tích điểm trong báo cáo). Chuông thông báo trên header hiển
thị số chưa đọc cho mọi user đã đăng nhập.

## Admin (role EMPLOYEE/ADMIN) — `src/app/(admin)/admin/` (Ant Design, root layout riêng — D-18)
| Route | Trang | Quyền | API |
|---|---|---|---|
| `/admin` | Dashboard: doanh thu, số đơn theo trạng thái, biểu đồ theo ngày/tháng/năm, top mặt hàng. Employee vào `/admin` được chuyển tới `/admin/orders` | Admin | `/admin/analytics/*` |
| `/admin/orders` | Danh sách đơn, lọc trạng thái/phương thức/ngày, tìm mã đơn/email/SĐT | Staff | `GET /admin/orders` |
| `/admin/orders/[id]` | Chi tiết đơn + lịch sử; nút chuyển trạng thái hợp lệ (kèm ghi chú) | Staff | `/admin/orders/{id}`, `PATCH .../status` |
| `/admin/returns` | Yêu cầu trả hàng: lọc trạng thái; duyệt / từ chối / xác nhận đã nhận hàng | Staff | `/admin/returns` |
| `/admin/inventory` | Tồn kho sản phẩm + combo (stock/reserved/available, cảnh báo sắp hết), form điều chỉnh, tab nhật ký | Staff | `/admin/inventory*` |
| `/admin/support` | Danh sách hội thoại + khung chat | Staff | `/admin/support/*`, WS |
| `/admin/users` | Danh sách, tìm, chi tiết, sửa role/khoá | Admin | `/admin/users` |
| `/admin/categories` | Cây danh mục: thêm, sửa, đổi cha, xoá | Admin | `/admin/categories` |
| `/admin/products`, `/admin/products/new`, `/admin/products/[id]` | Sản phẩm: danh sách, tạo, sửa (upload ảnh) | Admin | `/admin/products`, `/admin/uploads/images` |
| `/admin/combos`, `/admin/combos/new`, `/admin/combos/[id]` | Combo | Admin | `/admin/combos` |
| `/admin/vouchers` | Voucher: danh sách, thêm/sửa (modal), xoá/tắt | Admin | `/admin/vouchers` |
| `/admin/campaigns`, `/admin/campaigns/new`, `/admin/campaigns/[id]` | Chiến dịch giảm giá + chọn sản phẩm | Admin | `/admin/campaigns` |
| `/admin/articles`, `/admin/articles/new`, `/admin/articles/[id]` | Bài viết: danh sách, soạn (rich text), xuất bản | Admin | `/admin/articles` |

Khung admin đã có (`AdminShell`): sidebar theo quyền khai báo ở `src/lib/admin/nav.ts`; trang có trong
menu nhưng chưa làm hiển thị "đang xây dựng"; khách hàng vào `/admin` thấy trang 403. Phiên đăng nhập là
**thật**: `AuthProvider` (`components/features/auth/`) khôi phục phiên bằng `GET /users/me`; chưa đăng nhập →
`/login?redirect=...`; lỗi mạng khi khôi phục phiên hiện nút "Thử lại" (không chuyển hướng, giữ token). Khi thử
cần đăng nhập bằng 3 tài khoản mẫu (`local-development.md`); không còn ô chọn role "Dev".

Layout admin: sidebar theo quyền (Employee chỉ thấy Đơn hàng, Trả hàng, Tồn kho, Hỗ trợ). Truy cập
trang không đủ quyền → trang 403; chưa đăng nhập → `/login?redirect=...`. Backend luôn kiểm tra lại.

## Đã bỏ
- `/account/addresses` (D-02), `/admin/promotions` (tách thành `/admin/vouchers` + `/admin/campaigns`),
  `/rentals`, `/account/rentals` (D-01).
