# Lessons Learned

Ghi lại bài học trong quá trình làm (kỹ thuật lẫn quy trình nhóm) — hữu ích khi viết phần
"kết luận và hướng phát triển" của báo cáo đồ án.

## Mẫu
### <Chủ đề>
- **Bài học:**
- **Áp dụng lại lần sau:**

---

### `catch` chung nuốt lỗi điều khiển luồng của framework
- **Bài học:** `serverApi.request` bọc `fetch(..., { cache: "no-store" })` trong `catch` rồi đổi mọi lỗi thành `NETWORK_ERROR`. Lúc `next build`
  Next ném `DYNAMIC_SERVER_USAGE` (để chuyển trang sang render động) và `catch` đã nuốt nó, nên build lỗi `NETWORK_ERROR` ở `/`, `/brands`, `/products`
  dù backend vẫn chạy; CI không có backend nên đỏ. Chỉ lộ khi in lỗi gốc, không lộ khi chỉ đọc thông báo "Không kết nối được máy chủ".
- **Áp dụng lại lần sau:** `catch` rộng quanh lời gọi của framework phải cho lỗi điều khiển luồng (có `digest`) đi tiếp. Gặp thông báo lỗi chung chung
  thì in lỗi gốc trước khi kết luận nguyên nhân. Chạy `npm run build` KHÔNG có backend (như CI) trước khi mở PR.

### PR mở nhầm base `master` thay vì `dev`
- **Bài học:** GitHub chọn mặc định base là nhánh mặc định của kho (`master`), nên PR dễ vào nhầm. Khi đó cả lịch sử
  `dev` bị kéo vào `master`. Ngoài ra workflow CI (`backend-ci.yml`, `frontend-ci.yml`) chỉ kích hoạt cho PR vào `dev`
  và `main`, nhưng nhánh chính thật tên là `master` → PR vào `master` không chạy CI. Tài liệu (`git.md`) cũng ghi `main`.
- **Áp dụng lại lần sau:** Luôn kiểm tra ô `base: dev` trước khi bấm Create pull request. Nhóm nên thống nhất tên nhánh
  chính và sửa workflow/tài liệu cho khớp (hoặc bật branch protection).

### Công cụ mới hơn thư viện: Docker Desktop 29 và Testcontainers cũ
- **Bài học:** Lỗi chỉ xuất hiện trên máy dùng Docker mới còn CI (Docker cũ hơn) vẫn xanh, nên CI xanh không đảm bảo máy
  mọi thành viên chạy được.
- **Áp dụng lại lần sau:** Khi một thành viên báo "Docker chạy mà test không chạy", kiểm tra phiên bản thư viện so với
  phiên bản Docker; ghi cách xử lý vào `troubleshooting.md`.

### Token cũ còn trong trình duyệt chặn đăng nhập
- **Bài học:** Backend từ chối mọi token sai/hết hạn ngay cả ở endpoint công khai (đúng `authentication.md`). Frontend tự gắn
  token vào mọi request, nên người dùng còn token cũ trong `localStorage` bị 401 `UNAUTHORIZED` ngay ở bước đăng nhập, trước
  cả khi xét mật khẩu. Lỗi chỉ lộ khi thử với token cũ thật, không lộ khi test từng tầng riêng lẻ.
- **Áp dụng lại lần sau:** Endpoint xác thực công khai dùng `skipAuth`; khi làm luồng đầu-cuối luôn thử cả kịch bản
  "đã có token cũ".

### Không để task "nền" tồn đọng chặn cả nhóm
- **Bài học:** Chuỗi migration F-05 → F-09 phải tuần tự (số phiên bản Flyway) nên người sau phải chờ người trước; F-11
  (interface + stub) chỉ làm được khi cả chuỗi xong. Một seed dùng số phiên bản lớn (V6) làm sớm sẽ gây lỗi thứ tự.
- **Áp dụng lại lần sau:** Thống nhất trước số phiên bản migration cho từng người và làm song song; seed dev chỉ làm sau
  khi các bảng đã vào `dev` (D-19).
