# Troubleshooting

Ghi lại các lỗi hay gặp trong quá trình phát triển và cách xử lý, để không phải debug lại
từ đầu mỗi lần gặp.

## Mẫu
### <Tên lỗi ngắn gọn>
- **Triệu chứng:**
- **Nguyên nhân:**
- **Cách xử lý:**

---

### Backend không kết nối được PostgreSQL: `invalid value for parameter "TimeZone": "Asia/Saigon"`
- **Triệu chứng:** App dừng lúc khởi động, lỗi `FATAL: invalid value for parameter "TimeZone"` (thường từ Flyway).
- **Nguyên nhân:** JVM trên Windows báo múi giờ cũ `Asia/Saigon`; PostgreSQL 16 từ chối tên này.
- **Cách xử lý:** Đặt `JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Ho_Chi_Minh` (biến môi trường), hoặc chạy
  `mvn -DskipTests spring-boot:run "-Dspring-boot.run.jvmArguments=-Duser.timezone=Asia/Ho_Chi_Minh"`.

### App báo `Schema-validation: missing table [...]` khi khởi động
- **Triệu chứng:** `Failed to initialize JPA EntityManagerFactory ... missing table [cart_items]`.
- **Nguyên nhân:** `application.yml` đặt `ddl-auto: validate`, nhưng entity của module khác (cart, order, catalog...) chưa
  có migration tạo bảng (F-06 → F-09 chưa merge).
- **Cách xử lý:** Tạm đặt `spring.jpa.hibernate.ddl-auto: none` trong `application-dev.yml` (file local, không commit);
  đổi lại `validate` khi các bảng đã có.

### Flyway: `Found more than one migration with version 1` (hoặc `Validate failed`)
- **Triệu chứng:** App/test dừng với lỗi Flyway sau khi pull code có migration bị xoá hoặc đổi tên.
- **Nguyên nhân:** `backend/target/classes` còn file migration cũ (Maven không tự xoá file đã bị xoá khỏi source); hoặc
  lịch sử Flyway trong DB local không khớp migration mới.
- **Cách xử lý:** `mvn clean` rồi chạy lại. Nếu DB local đã áp dụng migration cũ: reset DB
  (`docker compose down -v` rồi `docker compose up -d`; mất dữ liệu local) và chèn lại tài khoản mẫu
  (`local-development.md`).

### Test Testcontainers báo `Could not find a valid Docker environment` dù Docker đang chạy
- **Triệu chứng:** `docker ps` chạy bình thường nhưng test dùng PostgreSQL Testcontainers lỗi, log có `Status 400`.
- **Nguyên nhân:** Testcontainers 1.19.8 (bản Spring Boot 3.3 quản lý) dùng API Docker cũ; Docker Desktop đời mới (29.x,
  API ≥ 1.44) từ chối.
- **Cách xử lý:** Dự án đã ghi đè `testcontainers.version=1.21.4` trong `pom.xml`. Nếu vẫn lỗi: cập nhật Docker Desktop /
  kiểm tra `docker version`. CI (Docker cũ hơn) không gặp lỗi này nên không phát hiện được.

### Mở Swagger / API bị hỏi username và password
- **Triệu chứng:** `http://localhost:8080/swagger-ui.html` hiện form hoặc hộp thoại đăng nhập.
- **Nguyên nhân:** Đang chạy bản build cũ thiếu cấu hình bảo mật hiện tại (mặc định Spring Security chặn tất cả; username
  `user`, mật khẩu ngẫu nhiên in trong log khởi động).
- **Cách xử lý:** Dừng app, `mvn clean spring-boot:run -DskipTests` lại; thử cửa sổ ẩn danh nếu trình duyệt giữ cache.

### `mvn spring-boot:run` báo "Could not find or load main class vn.campvivo.CampvivoApplication" (Windows)
- **Triệu chứng:** `mvn package`/`mvn test` chạy được, riêng `mvn spring-boot:run` báo `ClassNotFoundException`.
- **Nguyên nhân:** Đường dẫn dự án có ký tự ngoài bảng mã ANSI của Windows (vd. `D:\ĐA\...`). Trên Windows,
  spring-boot-maven-plugin ghi classpath ra một argfile dạng UTF-8, còn trình khởi chạy `java` đọc argfile theo
  bảng mã hệ thống (cp1252) → "Đ" bị đọc sai, classpath trỏ tới thư mục không tồn tại. Bản plugin mới hơn vẫn vậy.
- **Cách xử lý (chọn một):**
  - Tạo junction tên ASCII trỏ vào thư mục cũ (không cần đổi tên, không cần đóng IDE), rồi chạy Maven từ đó
    bằng PowerShell/cmd (Git Bash tự đổi junction về đường dẫn thật nên vẫn lỗi):
    `New-Item -ItemType Junction -Path D:\DA -Target D:\ĐA` → `cd D:\DA\campvivo\backend; mvn spring-boot:run`.
  - Hoặc đổi tên thư mục chỉ dùng ký tự ASCII (vd. `D:\DA\campvivo`).
  - Hoặc build rồi chạy jar: `mvn -DskipTests package` → `java -jar target/campvivo-backend-0.1.0-SNAPSHOT.jar`.
  - Hoặc bật "Beta: Use Unicode UTF-8 for worldwide language support" trong Region settings (toàn máy, cần khởi động lại).


### IPN VNPay không về khi chạy local
- **Triệu chứng:** Thanh toán xong trên sandbox nhưng đơn vẫn PENDING, rồi bị huỷ sau 15 phút.
- **Nguyên nhân:** VNPay không gọi được `localhost`; IPN URL chưa khai báo hoặc ngrok đổi URL.
- **Cách xử lý:** Chạy ngrok, cập nhật `app.vnpay.ipn-url` và IPN URL trên trang sandbox
  (`local-development.md`). Kiểm tra log endpoint `/api/payments/vnpay/ipn`.

### IPN báo sai chữ ký
- **Triệu chứng:** IPN trả `RspCode=97`.
- **Nguyên nhân:** Sai `VNPAY_HASH_SECRET`; không sắp xếp tham số theo alphabet hoặc encode khác
  cách VNPay; đưa cả `vnp_SecureHash`/`vnp_SecureHashType` vào chuỗi ký.
- **Cách xử lý:** Đối chiếu thuật toán với code mẫu Java của VNPay; viết unit test với bộ tham số mẫu.

### Deadlock khi test đặt hàng song song
- **Triệu chứng:** `PSQLException: deadlock detected`.
- **Nguyên nhân:** Khoá products/combos/voucher không theo thứ tự cố định.
- **Cách xử lý:** Theo thứ tự `orders → products (id tăng) → product_combos (id tăng) → vouchers`
  (`database.md` mục 6); truy vấn khoá nhiều dòng phải có `ORDER BY id`.

### WebSocket kết nối được nhưng không nhận thông báo
- **Triệu chứng:** Client subscribe `/user/queue/notifications` nhưng không có tin.
- **Nguyên nhân:** Chưa gắn `Principal` khi CONNECT, hoặc tên principal khác giá trị dùng trong
  `convertAndSendToUser` (phải là `userId` dạng chuỗi).
- **Cách xử lý:** Kiểm tra `ChannelInterceptor` xác thực JWT và `setUser(...)`.

### Không nhận được email OTP
- **Triệu chứng:** Đăng ký trả lỗi `EMAIL_SEND_FAILED` hoặc không thấy thư.
- **Nguyên nhân:** Sai cấu hình SMTP, Gmail chặn mật khẩu thường.
- **Cách xử lý:** Dùng App Password hoặc Mailpit khi dev.
