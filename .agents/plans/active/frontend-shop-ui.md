# Plan: Giao diện cửa hàng (frontend) — dữ liệu mock

## Mục tiêu
Đưa bộ giao diện cửa hàng desktop đã dựng sẵn (trang chủ, danh mục, chi tiết sản phẩm, tìm kiếm,
giỏ hàng + thanh toán mock, tài khoản, thương hiệu, blog, trang chính sách) vào `frontend/`, theo
route trong `docs/03-san-pham/pages.md`. Hiện chạy bằng dữ liệu mock; sau này thay bằng API backend.

## Phạm vi (trong/ngoài)
- Trong phạm vi: chuyển UI và hành vi phía client (giỏ hàng, đăng nhập giả bằng OTP, đặt hàng giả
  lưu localStorage), đổi route theo `pages.md`, đặt dữ liệu mock và các service đọc dữ liệu.
- Ngoài phạm vi:
  - nối API backend thật;
  - giao diện mobile (mới có desktop);
  - trang admin;
  - `/login`, `/register` riêng (hiện đăng nhập bằng popup OTP);
  - thay ảnh, nội dung mẫu bằng dữ liệu thật của Campvivo.

## Route
| Route | Trang |
|---|---|
| `/` | Trang chủ |
| `/search?q=` | Tìm kiếm (tab Sản phẩm / Bài viết) |
| `/products` | Trang tổng hợp sản phẩm |
| `/products/[slug]` | Chi tiết sản phẩm |
| `/categories/[slug]` | Danh mục (mới, chưa có trong pages.md) |
| `/brands`, `/brands/[slug]` | Thương hiệu (mới) |
| `/cart` | Giỏ hàng + thanh toán (một trang) |
| `/checkout` | Chuyển hướng về `/cart` |
| `/checkout/success?id=` | Đặt hàng thành công (mới) |
| `/account` | Tài khoản: đơn hàng, yêu thích, địa chỉ, điểm |
| `/blog`, `/blog/page/[n]`, `/blog/[slug]`, `/blog/category/[slug]` | Bài viết (mới) |
| `/guides` | Trang kiến thức (mới) |
| `/pages/[slug]` | Trang chính sách / giới thiệu (mới) |
| `/promotions/[slug]` | Trang khuyến mãi (mới) |

## Cấu trúc
- `src/app/(shop)/…`: các route ở trên. `src/app/account/` có layout riêng.
- `src/components/features/<module>/`: component nghiệp vụ.
- `src/components/ui/`: Lightbox, Carousel, SmartLink, icons.
- `src/services/catalogService.ts`: đọc dữ liệu mock phía server. Thay bằng `apiClient` khi backend sẵn sàng.
- `src/mocks/data/`: JSON mock.
- `src/styles/shop/`: CSS, mỗi loại trang scope theo `.pg-<page>`.
- `public/assets/`: font, icon, ảnh mẫu.

## Trạng thái
- [x] Đang làm
- [x] Đã test (lint, build, chạy thử luồng mua hàng)
- [x] Đã cập nhật docs/ (pages.md)
- [ ] Đã mở PR

## Lệch so với Phase1_v4 / docs 2026-10-17 — xử lý khi nối API
Xem `docs/03-san-pham/pages.md` (cột API, trạng thái) và `docs/99-ghi-chu/known-issues.md` KI-05.
- Đăng nhập OTP giả (`LoginPopup`) → đăng nhập email + mật khẩu; tách UI nhập OTP thành
  `components/ui/OtpInput` dùng cho `/verify-email` và `/reset-password`.
- `/account` đang là một trang nhiều tab → tách route con `/account/orders`, `/account/orders/[id]`,
  `/account/reviews`, `/account/favorites`, `/account/notifications`, `/account/support`. Bỏ tab
  "địa chỉ" (địa chỉ là trường trong hồ sơ — D-02) và tab "điểm" (không có trong báo cáo).
- `ShopProvider` (giỏ hàng, đơn hàng localStorage) → `cartService`, `orderService`. Giỏ hàng chỉ cho
  Customer đã đăng nhập; giá luôn lấy từ server; đặt hàng gửi `expectedTotal`.
- Thanh toán: chỉ VNPay + COD; `/checkout/success` đọc kết quả qua `GET /payments/vnpay/return`.
- Thương hiệu đọc từ `products.brand` (`GET /brands`); blog/journal/guides đọc `articles.category`.
  `/blog/category/*`, `/journal/category/*` không có nguồn dữ liệu → bỏ khi nối API.
- Chưa có: `/combos`, `/combos/[slug]`, tab Combo trong `/search`, toàn bộ `/admin/*`, chuông thông
  báo, chat hỗ trợ, giao diện mobile.
