# Quy tắc Frontend (Next.js)

- Theo `docs/03-san-pham/ui-guidelines.md` và `docs/03-san-pham/pages.md` — route đã định
  sẵn, KHÔNG tự đặt route khác (vd. trang chi tiết sản phẩm là `/products/[slug]`, không
  tạo `/product-detail` hay `/products/detail`).
- Tái sử dụng `components/ui/` (Button, Modal, Toast, Input...) trước khi tạo component mới.
- Component nghiệp vụ đặt trong `components/features/<module>/`.
- Gọi API qua `services/<module>Service.ts`, không fetch trực tiếp trong component.
- Kiểu dữ liệu đồng bộ với `docs/02-ky-thuat/api.md` (OpenAPI), đặt trong `types/`.
- Mỗi trang/luồng chính phải có: loading state, empty state, error state — không bỏ qua.
- Responsive bắt buộc: test ở mobile (~375px), tablet (~768px), desktop (~1280px).
- Không dùng `any` trong TypeScript trừ khi có comment giải thích rõ lý do.
- Trang admin (`app/admin/`) phải kiểm tra role phía client (ẩn/chặn UI) NHƯNG không được
  là lớp bảo vệ duy nhất — backend luôn phải chặn lại bằng RBAC.
