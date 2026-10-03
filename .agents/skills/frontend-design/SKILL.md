---
name: frontend-design
description: Thiết kế UI/UX cho các trang Campvivo trước khi code — xác định layout,
  typography, trạng thái loading/empty/error, responsive, trước khi chuyển sang
  frontend-implementation.
---

# Frontend Design

## Khi nào dùng
Trước khi code bất kỳ trang/luồng mới nào; KHÔNG dùng cho việc sửa nhỏ (đổi màu 1 nút...).

## Trước khi thiết kế
1. Đọc `docs/03-san-pham/pages.md` để biết route chính xác.
2. Đọc `docs/03-san-pham/ui-guidelines.md` (màu, typography, spacing, component dùng chung).
3. Xem các trang đã có trong `frontend/src/app/` và `components/ui/` để tái sử dụng.

## Yêu cầu thiết kế
1. Visual hierarchy rõ ràng — thông tin quan trọng nhất (giá, nút mua, trạng thái tồn kho)
   nổi bật nhất.
2. Dùng lại design token/component đã có, không tự nghĩ ra màu/spacing mới.
3. Bắt buộc có: loading state, empty state (vd. giỏ hàng rỗng), error state (vd. hết hàng,
   thanh toán thất bại).
4. Responsive: desktop, tablet, mobile.
5. Với trang Admin: ưu tiên mật độ thông tin cao, bảng dữ liệu rõ ràng hơn là hiệu ứng trang trí.

## Tiêu chí hoàn thành
- Thiết kế khớp route trong `pages.md`.
- Đủ 3 trạng thái loading/empty/error cho luồng chính.
- Không tạo component trùng với `components/ui/` đã có.
