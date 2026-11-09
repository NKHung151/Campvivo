# Lessons Learned

Ghi lại bài học trong quá trình làm (kỹ thuật lẫn quy trình nhóm) — hữu ích khi viết phần
"kết luận và hướng phát triển" của báo cáo đồ án.

## Mẫu
### <Chủ đề>
- **Bài học:**
- **Áp dụng lại lần sau:**

---

### Số migration Flyway: kiểm tra `dev` mới trước khi mở PR, và nhớ `db/seed/` dùng chung dãy số
- **Bài học:** Migration của nhánh chat mang số `V7`, còn trống lúc tạo nhánh. Một tuần sau `dev` đã có `V8`–`V12` và thư mục `db/seed/`
  cũng đã dùng `V7` (cả hai thư mục chung MỘT dãy số Flyway). Flyway không bật `outOfOrder`, nên ai đã chạy `dev` (DB tới V12) mà gặp
  một migration số nhỏ hơn chưa áp thì backend không khởi động; CI dùng DB mới nên không lộ.
- **Áp dụng lại lần sau:** Trước khi mở PR có migration mới: `git fetch`, liệt kê số đã dùng ở CẢ `db/migration/` và `db/seed/` trên `origin/dev`,
  chọn số lớn hơn tất cả. Mỗi số chỉ dùng một lần. Báo đồng đội số vừa lấy.

### Test bằng client Java/Node không bắt được lỗi CORS của trình duyệt
- **Bài học:** WebSocket của N3-08 có test STOMP thật (Java) và script Node đều xanh, nhưng chat trên trình duyệt báo "Mất kết nối
  trực tiếp" (REST vẫn chạy nên tải lại trang vẫn thấy tin). Nguyên nhân: SockJS gọi `/ws/info` bằng XHR chế độ credentials "include",
  trình duyệt chặn nếu phản hồi thiếu `Access-Control-Allow-Credentials: true`; `SecurityConfig` đặt `allowCredentials=false`. Chỉ trình
  duyệt thật mới thi hành CORS, nên mọi test không phải trình duyệt đều bỏ sót. Chỉ tái hiện được khi điều khiển Chrome thật (puppeteer-core
  với Chrome có sẵn, cài ngoài repo).
- **Áp dụng lại lần sau:** Tính năng nói chuyện với backend từ trình duyệt phải được chạy ít nhất một lần trong trình duyệt thật trước khi
  báo "đã kiểm chứng". Với lỗi CORS, thêm test đọc header `Access-Control-*` của phản hồi khi gửi kèm `Origin` (xem
  `RealtimeIntegrationTest`), vì client Java/Node không có CORS.

### Test "bị từ chối" xanh vì mọi thứ đều hỏng
- **Bài học:** Test kiểm tra kết nối WebSocket bị từ chối ban đầu chỉ khẳng định "không kết nối được". Khi cố ý làm hỏng cấu hình để kiểm tra
  đột biến, bắt tay trả HTTP 500 cho MỌI kết nối nên các test từ chối vẫn xanh dù cơ chế xác thực đã bị gỡ. Test chỉ đáng tin khi khẳng định
  đúng LÝ DO thất bại (ở đây: server trả khung `ERROR` của STOMP sau bắt tay thành công, còn origin lạ bị chặn 403 ở bước bắt tay).
- **Áp dụng lại lần sau:** Với test "phải bị từ chối", khẳng định kiểu từ chối chứ không chỉ "thất bại"; kèm một test ngược "đúng thì vào được"
  để chứng minh hạ tầng vẫn hoạt động. Khi phá một cơ chế để kiểm tra đột biến, phá sao cho phần còn lại vẫn chạy được.

### Test xanh ở máy này, đỏ ở CI vì phụ thuộc thứ tự chạy
- **Bài học:** Các lớp test tích hợp dùng chung một PostgreSQL. Test đơn hàng và khuyến mãi tạo giỏ/đơn rồi không dọn, còn test catalog/combo xoá
  `products` ở đầu mỗi test nên lỗi `cart_items_product_id_fkey` nếu chạy SAU chúng (41 lỗi trên CI). Windows chạy các lớp theo thứ tự tên nên
  catalog chạy trước và luôn xanh; Linux (CI) chạy theo thứ tự khác. Chỉ tái hiện được khi ép đảo thứ tự (`-Dsurefire.runOrder=reversealphabetical`).
- **Áp dụng lại lần sau:** Test tạo dữ liệu thì tự dọn khi kết thúc (`@AfterEach`, ví dụ `TRUNCATE TABLE products, product_combos CASCADE` dọn mọi bảng
  tham chiếu); không được giả định thứ tự chạy. Trước khi mở PR chạy thêm bộ test với `-Dsurefire.runOrder=reversealphabetical`.

### Bộ test tích hợp cạn kết nối PostgreSQL khi thêm lớp test mới
- **Bài học:** Mỗi lớp test tích hợp có Spring context riêng và được giữ trong cache tới hết lần chạy; mỗi context mở khoảng 10 kết nối (mặc định
  Hikari) tới PostgreSQL Testcontainers dùng chung (`max_connections` = 100). Khi số lớp test tăng, một lớp mới thêm vào làm `UserRepositoryTest`
  (không liên quan) lỗi `FATAL: sorry, too many clients already`. Cùng lúc đó một test giả định "đường dẫn này chưa có controller" (nên mong 404) hỏng khi
  module khác thêm controller thật: test nên dùng đường dẫn thăm dò không bao giờ có controller thay vì đường dẫn thật của tính năng sắp làm.
- **Áp dụng lại lần sau:** Test dùng DB dùng chung phải giới hạn pool (`spring.datasource.hikari.maximum-pool-size=5` trong `PostgresTestContainerConfig`).
  Test lỗi ở chỗ không liên quan tới thay đổi vừa làm thì xem log tìm nguyên nhân gốc (`Caused by`) trước khi đoán. Chạy `mvn clean test` toàn bộ
  trên `dev` mới trước khi bắt đầu việc mới để có mốc.

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
