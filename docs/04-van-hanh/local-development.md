# Chạy dự án local

## Yêu cầu
- JDK 17+, Maven
- Node.js 18+
- Docker (chạy PostgreSQL local, xem `docker.md`)

## Backend
```bash
cd backend
cp src/main/resources/application-dev.yml.example src/main/resources/application-dev.yml
# điền DB url/user/pass, không commit file đã điền secret thật
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
Flyway tự chạy migration khi khởi động. Health check: `GET /actuator/health`.

## Frontend
```bash
cd frontend
cp .env.example .env.local
npm install
npm run dev
```
Mặc định chạy ở `http://localhost:3000`, gọi API tại `NEXT_PUBLIC_API_URL` trong `.env.local`.

## Tài khoản mẫu (seed data, khi đã có)
Xem `backend/src/main/resources/db/migration/` cho seed role/user mẫu, hoặc tạo tài khoản
Admin thủ công qua `/auth/register` rồi set role trực tiếp trong DB lúc dev.
