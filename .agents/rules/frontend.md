# Quy tắc Frontend (Next.js)

- Theo `docs/03-san-pham/ui-guidelines.md` và `docs/03-san-pham/pages.md` — route đã định
  sẵn, KHÔNG tự đặt route khác (vd. trang chi tiết sản phẩm là `/products/[slug]`, không
  tạo `/product-detail` hay `/products/detail`).
- Tái sử dụng `components/ui/` (Button, Modal, Toast, Input, OtpInput, PriceTag...) trước khi tạo
  component mới. Component nghiệp vụ đặt trong `components/features/<module>/`.
- Gọi API qua `services/<module>Service.ts` (dùng chung `services/apiClient.ts`), không fetch trực
  tiếp trong component. Kiểu dữ liệu đặt trong `types/`, khớp `docs/02-ky-thuat/api.md`.
- Mỗi trang/luồng chính phải có: loading state, empty state, error state — không bỏ qua. Hiển thị lỗi
  theo `errorCode` (`lib/errorMessages.ts`).
- **Xác thực**: đăng nhập bằng email + mật khẩu. OTP chỉ dùng cho xác thực email và đặt lại mật khẩu
  (tái sử dụng UI popup OTP hiện có thành `OtpInput`). Bỏ đăng nhập OTP giả.
- **Giá**: luôn hiển thị giá server trả về (`price` sau chiến dịch, `originalPrice` gạch ngang). Không
  tự tính giảm giá ở client. Đặt hàng gửi `expectedTotal` = `finalAmount` của `/orders/preview`;
  xử lý `PRICE_CHANGED` bằng hộp thoại xác nhận giá mới.
- **Chống gửi trùng**: nút đặt hàng / thanh toán / gửi form disable trong lúc chờ response.
- **Realtime**: một kết nối STOMP dùng chung (hook `useRealtime`) sau khi đăng nhập; mất kết nối thì
  tự kết nối lại và gọi REST để đồng bộ.
- **Chuyển từ mock sang API**: thay `services/catalogService.ts` và `ShopProvider` (giỏ, đơn, đăng
  nhập lưu localStorage) bằng `services/<module>Service.ts`; xoá dần `src/mocks/` và
  `services/mockContent.ts` khi API tương ứng sẵn sàng. Không để song song hai nguồn dữ liệu cho cùng
  một màn.
- Nội dung/ảnh mock của cửa hàng tham khảo phải được thay trước khi demo.
- Responsive bắt buộc: test ở mobile (~375px), tablet (~768px), desktop (~1280px).
- Không dùng `any` trong TypeScript trừ khi có comment giải thích rõ lý do.
- Trang admin (`app/admin/`) kiểm tra role phía client (ẩn menu/chặn UI, Employee chỉ thấy Đơn hàng,
  Trả hàng, Tồn kho, Hỗ trợ) NHƯNG không được là lớp bảo vệ duy nhất — backend luôn chặn bằng RBAC.
