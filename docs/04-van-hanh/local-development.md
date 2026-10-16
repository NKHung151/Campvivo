# Chạy dự án local

## Yêu cầu
- JDK 17+, Maven
- Node.js 18+
- Docker (chạy PostgreSQL local, xem `docker.md`)
- (Khi test VNPay) ngrok hoặc công cụ tương đương để VNPay gọi được IPN về máy local

## Backend
```bash
cd backend
cp src/main/resources/application-dev.yml.example src/main/resources/application-dev.yml
# điền DB url/user/pass, JWT_SECRET, SMTP, VNPAY_*; không commit file đã điền secret thật
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
Flyway tự chạy migration khi khởi động (`ddl-auto=validate`). Health check: `GET /actuator/health`.

**Email khi dev:** dùng SMTP thật (Gmail App Password) hoặc Mailpit/MailHog (`localhost:1025`, xem
thư ở `localhost:8025`) để nhận OTP.

**VNPay sandbox:**
1. Đăng ký tài khoản merchant sandbox tại VNPay, lấy `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`.
2. `ngrok http 8080` → lấy URL public, đặt `app.vnpay.ipn-url = https://<ngrok>/api/payments/vnpay/ipn`
   và khai báo IPN URL này trong trang quản trị sandbox của VNPay.
3. `app.vnpay.return-url = http://localhost:3000/checkout/success`.
4. Dùng thẻ test do VNPay cung cấp trong tài liệu sandbox.

## Frontend
```bash
cd frontend
cp .env.example .env.local
npm install
npm run dev
```
Mặc định chạy ở `http://localhost:3000`, gọi API tại `NEXT_PUBLIC_API_URL`
(`http://localhost:8080/api`), WebSocket tại `NEXT_PUBLIC_WS_URL` (`http://localhost:8080/ws`).

## Tài khoản mẫu (seed profile dev — `database.md` mục 7, khi đã có)
| Role | Email | Mật khẩu |
|---|---|---|
| ADMIN | admin@campvivo.local | Admin@123 |
| EMPLOYEE | staff@campvivo.local | Staff@123 |
| CUSTOMER | customer@campvivo.local | Customer@123 |

Chỉ dùng cho môi trường dev; không seed các tài khoản này ở production.

## Lệnh chuẩn
`./scripts/agent-build.sh`, `./scripts/agent-lint.sh`, `./scripts/agent-test.sh` (xem `AGENTS.md` mục 11).
