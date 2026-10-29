# Docker

`docker-compose.yml` ở root chạy PostgreSQL (và pgAdmin tuỳ chọn) cho môi trường dev local.

```bash
docker compose up -d
```

Image là `pgvector/pgvector:pg16` = PostgreSQL 16 + extension pgvector (chatbot, D-27). Test backend (Testcontainers)
và CI dùng cùng image. Máy đã chạy `postgres:16` trước đây chỉ cần `docker compose up -d` lại (image mới, cùng bản 16
nên volume `campvivo_pgdata` dùng tiếp được, không mất dữ liệu).

Khi có đủ backend/frontend Dockerfile, bổ sung service `backend` và `frontend` vào
`docker-compose.yml` để chạy toàn bộ stack bằng 1 lệnh. Cấu hình chi tiết: `infra/docker/`.

Không commit file `.env` thật vào repo — chỉ commit `.env.example`.
