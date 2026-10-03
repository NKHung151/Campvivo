#!/usr/bin/env bash
# Lệnh test chuẩn — dùng trước khi coi 1 task là "xong" (xem AGENTS.md mục 7).
set -euo pipefail
echo "== Backend test (mvn test, bao gồm Testcontainers IT) =="
(cd backend && mvn -q test)
echo "== Frontend build check (Next.js không có unit test mặc định ở scaffold này) =="
(cd frontend && npm run build)
