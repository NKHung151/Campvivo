# Tổng quan dự án Campvivo

Hệ thống thương mại điện tử chuyên kinh doanh trang thiết bị dã ngoại và du lịch trải nghiệm
(lều, túi ngủ, bếp, balo… và combo đóng gói sẵn), kèm nội dung kiến thức outdoor và hỗ trợ khách
hàng trực tuyến. Đồ án tốt nghiệp ngành Công nghệ phần mềm, nhóm 4 người, 12 tuần.

Nguồn yêu cầu gốc: báo cáo Phase1_v4 (ngoài repo). Ánh xạ use case → code: `docs/03-san-pham/use-cases.md`.

## Stack
Backend Spring Boot · Frontend Next.js/React · PostgreSQL · WebSocket (STOMP) · VNPay sandbox.
Chi tiết: `docs/02-ky-thuat/tech-stack.md`.

## Năm phân hệ chức năng (theo báo cáo, bảng 2.1)
| Phân hệ | Chức năng chính | Tier |
|---|---|---|
| 1. Xác thực và thông tin người dùng | Đăng ký + xác thực email (OTP), đăng nhập/đăng xuất, quên mật khẩu, hồ sơ, đổi mật khẩu | 1 |
| 2. Quản trị hệ thống | Người dùng, đơn hàng, sản phẩm, danh mục, tồn kho (Tier 1); combo, voucher, chiến dịch giảm giá, bài viết, xử lý trả hàng, dashboard doanh số (Tier 2) | 1 + 2 |
| 3. Mua hàng | Giỏ hàng, đặt hàng, thanh toán VNPay/COD, đơn cá nhân (Tier 1); đánh giá, trả hàng (Tier 2) | 1 + 2 |
| 4. Khám phá và hỗ trợ | Trang chủ, tìm kiếm, xem sản phẩm/combo (Tier 1); bài viết, yêu thích, chat hỗ trợ (Tier 2) | 1 + 2 |
| 5. Hệ thống | Giữ chỗ/giải phóng tồn kho (Tier 1); thông báo realtime, email, gợi ý mua kèm (Tier 2) | 1 + 2 |

Tier 3 (chưa làm): thanh toán MoMo, chatbot AI. Danh sách FR đầy đủ: `docs/03-san-pham/features.md`.

## Tác nhân
`Guest → User → Customer`; `Employee`, `Admin` là tác nhân vận hành; `System` là tác nhân ảo
(job, IPN, gửi thông báo/email). Quyền chi tiết: `docs/02-ky-thuat/authentication.md`.

## Bài toán kỹ thuật trọng tâm
- Chống bán vượt tồn kho khi nhiều khách đặt cùng lúc (giữ chỗ + khoá bi quan, áp cho cả sản phẩm và combo).
- Chống vượt số lượt voucher khi dùng đồng thời.
- IPN thanh toán idempotent, xác minh chữ ký.
- Thông báo và chat thời gian thực.
- Danh mục đa cấp bằng materialized path; gợi ý sản phẩm theo lịch sử mua chung.

## Phân công nhân sự (theo 4 module cốt lõi của báo cáo — nhóm xác nhận lại)
| Người | Module báo cáo | Module backend | Phần frontend |
|---|---|---|---|
| Người 1 | Mua hàng | `cart`, `order` (gồm trả hàng), `payment` | giỏ hàng/thanh toán, đơn cá nhân, admin đơn hàng/trả hàng |
| Người 2 | Quản trị hệ thống | `catalog`, `combo`, `inventory`, `promotion`, `article`, `analytics` | admin sản phẩm/danh mục/combo/tồn kho/voucher/chiến dịch/bài viết/dashboard |
| Người 3 | Xác thực và thông tin người dùng | `auth`, `user`, `common` (security, websocket, exception) | layout, `components/ui`, trang auth/hồ sơ, admin user |
| Người 4 | Khám phá và hỗ trợ + Hệ thống | `review`, `favorite`, `support`, `notification`, `recommendation` | trang khám phá/tìm kiếm/bài viết, đánh giá, yêu thích, chat, thông báo |

Chi tiết timeline 12 tuần: tài liệu kế hoạch riêng của nhóm (ngoài repo).
