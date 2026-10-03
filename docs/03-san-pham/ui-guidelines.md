# UI Guidelines

> Nhóm điền cụ thể màu sắc/typography đã thống nhất vào đây. Dưới đây là khung mặc định —
> agent dùng tạm nếu chưa có quyết định cuối cùng, và PHẢI theo đúng khi nhóm đã chốt.

## Nguyên tắc
- Ưu tiên rõ ràng, dễ quét thông tin hơn là trang trí — đây là site bán hàng, không phải
  portfolio nghệ thuật.
- Màu chủ đạo gợi thiên nhiên/dã ngoại (xanh rừng, nâu đất, cam cháy...) — chốt mã màu cụ
  thể trước khi code hàng loạt component.
- Typography: 1 font chính cho heading, 1 font cho body, tối đa 4-5 cấp kích thước.
- Spacing theo thang chuẩn (4px/8px grid) để đồng nhất toàn site.

## Component dùng chung bắt buộc tái sử dụng
`Button` (primary/secondary/danger), `Card` (dùng cho ProductCard, OrderCard...), `Modal`,
`Toast` (thông báo thành công/lỗi), `Input`, `Select`, `Pagination`.
Vị trí: `frontend/src/components/ui/`.

## Trạng thái bắt buộc cho mọi trang có dữ liệu động
- Loading: skeleton hoặc spinner, không để trắng trang.
- Empty: thông điệp rõ ràng + hành động gợi ý (vd. giỏ hàng rỗng → nút "Tiếp tục mua sắm").
- Error: thông báo lỗi dễ hiểu bằng tiếng Việt, không hiển thị lỗi kỹ thuật thô (stack trace,
  mã lỗi HTTP) trực tiếp cho người dùng cuối.

## Responsive
Mobile-first, breakpoint tối thiểu: ~375px (mobile), ~768px (tablet), ~1280px (desktop).
