# Docker

`docker-compose.yml` ở root chạy PostgreSQL (và pgAdmin tuỳ chọn) cho môi trường dev local.

```bash
docker compose up -d
```

Khi có đủ backend/frontend Dockerfile, bổ sung service `backend` và `frontend` vào
`docker-compose.yml` để chạy toàn bộ stack bằng 1 lệnh. Cấu hình chi tiết: `infra/docker/`.

Không commit file `.env` thật vào repo — chỉ commit `.env.example`.
