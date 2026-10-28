# Docker

`docker-compose.yml` ở root chạy cho môi trường dev local:
- **PostgreSQL** (`campvivo-postgres`, cổng 5432).
- **Mailpit** (`campvivo-mailpit`): hộp thư giả để nhận OTP/email khi dev. Backend gửi tới `localhost:1025`, đọc thư ở
  http://localhost:8025. Không gửi thư ra ngoài.

```bash
docker compose up -d            # bật cả hai
docker compose up -d mailpit    # chỉ bật Mailpit (nếu Postgres đã chạy)
```

Khi có đủ backend/frontend Dockerfile, bổ sung service `backend` và `frontend` vào
`docker-compose.yml` để chạy toàn bộ stack bằng 1 lệnh. Cấu hình chi tiết: `infra/docker/`.

Không commit file `.env` thật vào repo — chỉ commit `.env.example`.
