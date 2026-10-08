# Danh sách trang (Routes)

Agent code frontend PHẢI dùng đúng route dưới đây — không tự đặt tên khác.

## Public (Guest)
| Route | Trang |
|---|---|
| `/` | Trang chủ |
| `/products` | Danh sách sản phẩm (filter theo category, giá) |
| `/products/[slug]` | Chi tiết sản phẩm |
| `/combos` | Danh sách combo |
| `/combos/[slug]` | Chi tiết combo |
| `/search` | Kết quả tìm kiếm |

### Route bổ sung cho storefront (thêm 2026-10-08, đang dùng dữ liệu mock)
| Route | Trang |
|---|---|
| `/search?q=` | Kết quả tìm kiếm: tab Sản phẩm / Bài viết |
| `/categories/[slug]` | Danh mục sản phẩm (bộ lọc, sắp xếp, so sánh) |
| `/brands`, `/brands/[slug]` | Danh sách thương hiệu, trang thương hiệu |
| `/blog`, `/blog/page/[n]`, `/blog/[slug]`, `/blog/category/[slug]` | Bài viết, phân trang, chi tiết, danh mục bài viết |
| `/guides` | Trang kiến thức / kinh nghiệm outdoor |
| `/pages/[slug]` | Trang chính sách, giới thiệu |
| `/promotions/[slug]` | Trang khuyến mãi |
| `/checkout/success?id=` | Đặt hàng thành công |
| `/categories/san-pham-moi` | Hàng mới về (New arrivals): banner + danh sách sản phẩm |
| `/journal`, `/journal/page/[n]`, `/journal/[slug]`, `/journal/category/[slug]` | Outdoor Journal: bài viết cộng đồng, phân trang, chi tiết, danh mục |

Ghi chú: hiện `/cart` gồm cả giỏ hàng và form thanh toán; `/checkout` chuyển hướng về `/cart`.

## Auth
| Route | Trang |
|---|---|
| `/login` | Đăng nhập |
| `/register` | Đăng ký |

## Customer (đã đăng nhập)
| Route | Trang |
|---|---|
| `/cart` | Giỏ hàng |
| `/checkout` | Thanh toán |
| `/account` | Hồ sơ cá nhân |
| `/account/addresses` | Sổ địa chỉ |
| `/account/orders` | Lịch sử đơn hàng |
| `/account/orders/[id]` | Chi tiết đơn hàng |

## Admin
| Route | Trang |
|---|---|
| `/admin` | Dashboard tổng quan |
| `/admin/products` | Quản lý sản phẩm |
| `/admin/categories` | Quản lý danh mục |
| `/admin/orders` | Quản lý đơn hàng |
| `/admin/promotions` | Quản lý khuyến mãi |
| `/admin/combos` | Quản lý combo |
| `/admin/analytics` | Báo cáo doanh thu |

## Tier 3 (chưa mở, đặt tên trước để tránh xung đột sau này)
| Route | Trang |
|---|---|
| `/rentals` | Danh sách sản phẩm cho thuê |
| `/account/rentals` | Đơn thuê của tôi |
