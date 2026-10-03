# Tổng quan dự án Campvivo

Hệ thống thương mại điện tử chuyên kinh doanh trang thiết bị dã ngoại và du lịch trải
nghiệm. Đồ án tốt nghiệp ngành Công nghệ phần mềm, nhóm 4 người, thời gian 12 tuần.

## Stack
Backend Spring Boot · Frontend Next.js/React · Database PostgreSQL.
Chi tiết: `docs/02-ky-thuat/tech-stack.md`.

## Phạm vi 3 tầng
| Tier | Nội dung | Ưu tiên |
|---|---|---|
| 1 | Auth, danh mục/sản phẩm/kho, giỏ hàng, đặt hàng, thanh toán, admin cơ bản | Bắt buộc, làm trước |
| 2 | Khuyến mãi, combo, đánh giá, thông báo tự động, dashboard, gợi ý sản phẩm | Làm song song từ tuần 3 |
| 3 | Cho thuê một nhóm sản phẩm nhỏ (lều, túi ngủ) | Phụ lục, chỉ mở sau mốc tuần 9 nếu Tier 1+2 ổn định |

Lý do chọn "bán là chính, thuê là phụ lục": xem `docs/99-ghi-chu/decisions.md`.

## Actor
Guest → User → (Customer | Employee | Admin). Chi tiết quyền hạn: `docs/02-ky-thuat/
authentication.md`.

## Phân công nhân sự
| Người | Phụ trách chính |
|---|---|
| Người 1 | Giỏ hàng, Đặt hàng, Thanh toán |
| Người 2 | Sản phẩm, Danh mục, Kho, Admin |
| Người 3 | Auth, Search, Hạ tầng/UI chung |
| Người 4 | Combo, Khuyến mãi, Đánh giá, Thông báo, Dashboard, Recommendation, QA/Tích hợp |

Chi tiết timeline 12 tuần: xem tài liệu kế hoạch riêng của nhóm (ngoài repo code).
