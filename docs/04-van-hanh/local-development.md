# Chạy dự án local

## Yêu cầu
- JDK 17+, Maven
- Node.js 18+
- PostgreSQL 15+: dùng Docker (`docker compose up -d`, xem `docker.md`) **hoặc** PostgreSQL cài trực
  tiếp trên máy. Cài trực tiếp thì tạo sẵn user + database (cùng thông số với `docker-compose.yml`):
  ```sql
  CREATE ROLE campvivo LOGIN PASSWORD 'changeme';
  CREATE DATABASE campvivo OWNER campvivo ENCODING 'UTF8' TEMPLATE template0;
  ```
  Không chạy cả hai cùng lúc — cùng chiếm cổng 5432.
- Docker Desktop vẫn cần để chạy test backend (Testcontainers).
- (Khi test VNPay) ngrok hoặc công cụ tương đương để VNPay gọi được IPN về máy local

## Thiết lập lần đầu / sau khi pull code mới (để đăng nhập được)
Làm theo thứ tự. Lệnh dạng PowerShell (Windows); trên macOS/Linux dùng `cp` thay `Copy-Item`.

1. **Lấy code mới:** `git switch dev` rồi `git pull origin dev` (đang làm dở nhánh riêng thì commit trước).
2. **Bật PostgreSQL:** `docker compose up -d`. Nếu trước đây đã chạy backend với migration cũ, **reset DB một lần**:
   `docker compose down -v` rồi `docker compose up -d` (xoá dữ liệu DB local; lịch sử Flyway cũ không khớp).
3. **Tạo cấu hình dev:** `Copy-Item backend/src/main/resources/application-dev.yml.example backend/src/main/resources/application-dev.yml`
   (file không được commit). Bản `.example` đã có `app.jwt.secret` (≥ 32 ký tự) và `ddl-auto: none`; nếu đổi sang
   `validate` backend sẽ báo `missing table [...]` cho đến khi F-06 → F-09 merge (xem `troubleshooting.md`).
4. **Chạy backend** (chờ dòng `Started CampvivoApplication`): `cd backend` rồi `mvn clean spring-boot:run -DskipTests`.
   Nếu báo lỗi `TimeZone "Asia/Saigon"` (Windows): đặt `$env:JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Ho_Chi_Minh"` trước khi chạy.
5. **Chèn 3 tài khoản mẫu** vào DB (mục "Tài khoản mẫu" bên dưới); làm lại sau mỗi lần reset DB.
6. **Chạy frontend:** `cd frontend`, `npm install`, `npm run dev`, mở `http://localhost:3000/login`. Frontend phải chạy
   đúng cổng 3000 (backend chỉ cho phép origin này, `app.cors.allowed-origins`).

## Backend
```bash
cd backend
cp src/main/resources/application-dev.yml.example src/main/resources/application-dev.yml
# điền DB url/user/pass, JWT_SECRET; VNP_*, GHN_*, SMTP... đặt ở .env gốc (backend tự nạp); không commit secret thật
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
Flyway tự chạy migration khi khởi động. Mặc định `application.yml` đặt `ddl-auto=validate`, nhưng `application-dev.yml.example`
đang tạm đặt `none` (xem bước 3 ở trên). Health check: `GET /actuator/health`; Swagger: `http://localhost:8080/swagger-ui.html`.

Profile dev nạp thêm dữ liệu mẫu catalog (`db/seed` — danh mục, sản phẩm, combo; xem `database.md` mục 7) nhờ dòng
`spring.flyway.locations: classpath:db/migration,classpath:db/seed` trong `application-dev.yml`.

**Windows, đường dẫn có ký tự tiếng Việt (vd. `D:\ĐA`):** `mvn spring-boot:run` báo `ClassNotFoundException` — xem
`troubleshooting.md` (chạy qua junction ASCII hoặc `java -jar`).

**Email khi dev (OTP):** `docker compose up -d` đã bật **Mailpit** (hộp thư giả, container `campvivo-mailpit`). Backend mặc
định gửi tới `localhost:1025` nên không cần cấu hình gì thêm; mở **http://localhost:8025** để đọc thư và lấy mã OTP (thư
không gửi ra ngoài). Muốn dùng SMTP thật (Gmail App Password) thì đặt `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`,
`SMTP_PASSWORD`, `SMTP_AUTH=true`, `SMTP_STARTTLS=true` (xem `tech-stack.md`). Mã OTP không bao giờ được ghi vào log.
Nếu chưa bật Mailpit, các chức năng gửi OTP sẽ trả `EMAIL_SEND_FAILED`.

**Thử đăng ký bằng PowerShell** (backend chạy ở 8080, Mailpit đang bật):
```powershell
$u = "http://localhost:8080/api"; $j = "application/json"
# 1. Đăng ký (đổi email tuỳ ý) → 201
Invoke-RestMethod -Method Post "$u/auth/register" -ContentType $j -Body '{"email":"ban@example.com","password":"Passw0rd1","fullName":"Tên Của Bạn"}'
# 2. Mở http://localhost:8025, mở thư "Mã xác thực email Campvivo", lấy mã 6 số
# 3. Xác thực email bằng mã đó → nhận token (đăng nhập luôn)
Invoke-RestMethod -Method Post "$u/auth/verify-email" -ContentType $j -Body '{"email":"ban@example.com","code":"123456"}'
# Gửi lại mã (chờ ít nhất 60 giây kể từ thư trước)
Invoke-RestMethod -Method Post "$u/auth/resend-code" -ContentType $j -Body '{"email":"ban@example.com","purpose":"VERIFY_EMAIL"}'
```
Chưa xác thực mà đăng nhập sẽ nhận `EMAIL_NOT_VERIFIED`. Xoá tài khoản thử:
`docker exec campvivo-postgres psql -U campvivo -d campvivo -c "delete from users where email='ban@example.com'"`.

**Biến môi trường từ `.env`:** backend tự nạp file `.env` ở gốc repo khi chạy từ thư mục `backend/`
(`spring.config.import`), nên `GHN_*`, `VNP_*`... không cần khai lại trong `application-dev.yml`.

**GHN staging (D-27):** điền `GHN_TOKEN`, `GHN_SHOP_ID`, `GHN_FROM_DISTRICT_ID`, `GHN_FROM_WARD_CODE` trong `.env` (mẫu
`.env.example`). Kiểm tra nhanh: `GET http://localhost:8080/api/shipping/provinces` trả danh sách tỉnh; trang `/cart` → "Thông
tin giao hàng" có 3 dropdown Tỉnh/Quận/Phường và dòng "Phí vận chuyển" tự tính lại. Lỗi `SHIPPING_GATEWAY_ERROR` → xem log
backend (dòng `GHN ... trả HTTP ...`), thường do thiếu biến hoặc mã phường/quận kho sai.

**VNPay sandbox (D-28):**
1. Đăng ký merchant sandbox, điền `VNP_TMN_CODE`, `VNP_HASH_SECRET`, `VNP_URL`, `VNP_RETURN_URL=http://localhost:3000/payment/vnpay-return`
   trong `.env`.
2. Ở localhost không cần IPN: trang `/payment/vnpay-return` gửi kết quả cho `GET /api/vnpay/return`, backend kiểm chữ ký và
   ghi nhận bằng cùng hàm với IPN. Muốn thử IPN thật: `ngrok http 8080` rồi khai báo `https://<ngrok>/api/vnpay/ipn` ở cổng
   quản trị sandbox VNPay.
3. Thẻ test (tài liệu sandbox VNPay): ngân hàng NCB, số thẻ `9704198526191432198`, tên `NGUYEN VAN A`, ngày phát hành `07/15`,
   OTP `123456`.
4. Thanh toán xong, đơn chuyển CONFIRMED và vận đơn GHN staging được tạo tự động (xem mã vận đơn ở trang kết quả).

## Frontend
```bash
cd frontend
cp .env.example .env.local
npm install
npm run dev
```
Mặc định chạy ở `http://localhost:3000`, gọi API tại `NEXT_PUBLIC_API_URL`
(`http://localhost:8080/api`), WebSocket tại `NEXT_PUBLIC_WS_URL` (`http://localhost:8080/ws`).

## Tài khoản mẫu (seed profile dev — `database.md` mục 7, khi đã có; hiện chèn tay, xem bên dưới)
| Role | Email | Mật khẩu |
|---|---|---|
| ADMIN | admin@campvivo.local | Admin@123 |
| EMPLOYEE | staff@campvivo.local | Staff@123 |
| CUSTOMER | customer@campvivo.local | Customer@123 |

Chỉ dùng cho môi trường dev; không seed các tài khoản này ở production.

**Tạm thời đến khi có seed chính thức (F-12, xem D-19):** tự chèn 3 tài khoản vào DB local. Lệnh idempotent
(chạy lại nhiều lần không lỗi, không ghi đè tài khoản đã có). Chạy ở thư mục gốc repo, khi container Postgres
đang chạy:
```bash
docker exec -i campvivo-postgres psql -U campvivo -d campvivo <<'SQL'
INSERT INTO users (email, password_hash, full_name, role, email_verified) VALUES
  ('admin@campvivo.local',    '$2a$10$1d9xWG4uRfdBTLokOuNzQuyS69CWwhE83qkd3JmHIp8XXroPmFLZW', 'Admin Dev',      'ADMIN',    true),
  ('staff@campvivo.local',    '$2a$10$WNM792SndqN0s2npCYDnrudtYKrr/u7IWrA8ZyXAhpyxSIWkLfWpm', 'Nhân viên Dev',  'EMPLOYEE', true),
  ('customer@campvivo.local', '$2a$10$6BAvQ1PpgOoUIhB1syMznOHka98mbJpfpWVVj3gQeP/wJdUiC1nB2', 'Khách hàng Dev', 'CUSTOMER', true)
ON CONFLICT (email) DO NOTHING;
SQL
```
Chuỗi `$2a$10$...` là mã băm BCrypt (strength 10) của mật khẩu ở bảng trên. Trên PowerShell, `<<'SQL'` không dùng được:
lưu đoạn SQL vào một file rồi `Get-Content file.sql | docker exec -i campvivo-postgres psql -U campvivo -d campvivo`.
Phải chạy lại sau mỗi lần reset DB (`docker compose down -v`). Đăng xuất làm tăng `token_version` của tài khoản đó
(đăng xuất mọi thiết bị) nhưng không ảnh hưởng việc đăng nhập lại.

## Lệnh chuẩn
`./scripts/agent-build.sh`, `./scripts/agent-lint.sh`, `./scripts/agent-test.sh` (xem `AGENTS.md` mục 11).
