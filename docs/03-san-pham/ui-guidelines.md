# UI Guidelines

> Màu sắc/typography cụ thể **chưa được nhóm chốt**. Storefront hiện tại (`frontend/src/styles/shop/`)
> dùng CSS riêng, font `SVN-Gilroy` cho tiêu đề và `Georgia`/`Merriweather` cho nội dung bài viết,
> chưa có design token. Trước khi sinh UI admin hàng loạt: trích màu/font từ storefront thành biến CSS
> (`:root` trong `styles/shop/base.css`) và cấu hình Tailwind `theme.extend` dùng chung các biến đó,
> rồi ghi giá trị vào mục "Token" bên dưới.

## Nguyên tắc
- Ưu tiên rõ ràng, dễ quét thông tin hơn là trang trí — đây là site bán hàng.
- Thông tin quan trọng nhất nổi bật nhất: giá (giá sau chiến dịch đậm, giá gốc gạch ngang), nút
  mua, tình trạng tồn kho, trạng thái đơn.
- Màu chủ đạo gợi thiên nhiên/dã ngoại (xanh rừng, nâu đất, cam cháy...).
- Typography: 1 font heading, 1 font body, tối đa 4–5 cấp kích thước. Spacing theo lưới 4px/8px.
- Ngôn ngữ giao diện: tiếng Việt. Tiền: `450.000₫`. Ngày giờ: `17/10/2026 09:15`.

## Token (điền khi chốt)
| Token | Giá trị | Dùng cho |
|---|---|---|
| `--color-primary` | (chưa chốt) | Nút chính, link |
| `--color-accent` | (chưa chốt) | Giá khuyến mãi, badge giảm giá |
| `--color-danger` / `--color-success` / `--color-warning` | (chưa chốt) | Lỗi / thành công / cảnh báo |
| `--font-heading` / `--font-body` | SVN-Gilroy / (chưa chốt) | |

## Component dùng chung bắt buộc tái sử dụng (`frontend/src/components/ui/`)
`Button` (primary/secondary/danger, trạng thái loading), `Card`, `Modal` (+ `ConfirmDialog`),
`Toast`, `Input`, `Textarea`, `Select`, `Checkbox`, `Pagination`, `Badge`, `Tabs`, `Skeleton`,
`EmptyState`, `ErrorState`, `PriceTag` (giá gốc + giá sau giảm), `QuantityInput`, `RatingStars`,
`OtpInput` (6 ô, tách từ popup OTP hiện có), `DataTable` (admin: cột, sắp xếp, phân trang).
Đã có: `Lightbox`, `Carousel`, `SmartLink`, icons.

## Badge trạng thái (thống nhất mọi nơi)
| Giá trị | Nhãn tiếng Việt | Màu |
|---|---|---|
| PENDING | Chờ xử lý (VNPay: Chờ thanh toán) | vàng |
| CONFIRMED | Đã xác nhận | xanh dương |
| SHIPPING | Đang giao | tím |
| COMPLETED | Hoàn tất | xanh lá |
| CANCELLED | Đã huỷ | xám |
| PAYMENT_FAILED | Thanh toán thất bại | đỏ |
| RETURNED | Đã trả hàng | cam |
| Return: PENDING / APPROVED / REJECTED / COMPLETED | Chờ duyệt / Đã duyệt / Từ chối / Đã nhận hàng | vàng / xanh dương / đỏ / xanh lá |

## Trạng thái bắt buộc cho mọi trang có dữ liệu động
- Loading: skeleton hoặc spinner, không để trắng trang. Nút gửi form disable + spinner khi đang gửi
  (chống bấm "Đặt hàng" hai lần).
- Empty: thông điệp rõ ràng + hành động gợi ý (giỏ hàng rỗng → "Tiếp tục mua sắm"; chưa có đơn →
  "Khám phá sản phẩm").
- Error: thông báo tiếng Việt theo `errorCode` (`lib/errorMessages.ts`), không hiển thị stack trace,
  mã HTTP. Có nút "Thử lại" khi lỗi mạng.
- Hết hàng: nút "Thêm vào giỏ" disable, nhãn "Hết hàng". Sắp hết (`available ≤ 5`): "Chỉ còn N sản phẩm".

## Khu vực admin (Ant Design — D-18)
- Dùng component antd, không viết lại: `Table` (phân trang/sort/lọc), `Form` + rule validate, `Upload`,
  `TreeSelect` (chọn danh mục cha), `DatePicker.RangePicker`, `Modal`, `Drawer`, `Tag`, `Result`.
- Thông báo dùng `App.useApp()` (`message`, `modal`, `notification`) — đã bọc `<App>` trong
  `AdminProviders`. Lỗi API hiển thị theo `errorCode` (`lib/errorMessages.ts`).
- Theme đặt một chỗ trong `components/admin/shell/AdminProviders.tsx` (màu chủ đạo `#2f6a3f`, locale
  `vi_VN`); không đặt màu cứng trong từng trang.
- Mỗi trang danh sách: thanh lọc phía trên, bảng, phân trang; trạng thái lọc/phân trang nằm trên URL
  query để F5 và chia sẻ link vẫn giữ.
- Trạng thái: loading dùng `loading` của Table/Button hoặc `Spin`; rỗng dùng `Empty`; lỗi dùng `Result`
  hoặc `Alert`. Nút gửi form có `loading` để chống bấm hai lần.
- Ưu tiên desktop (≥ 1280px); sidebar tự thu gọn ở màn hình nhỏ (`breakpoint="lg"`).

## Responsive
Mobile-first, breakpoint tối thiểu: ~375px (mobile), ~768px (tablet), ~1280px (desktop).
Storefront hiện mới có bản desktop — bổ sung mobile là việc còn nợ (xem `.agents/progress.md`).
Admin ưu tiên desktop (≥ 1280px), tối thiểu không vỡ layout ở tablet.
