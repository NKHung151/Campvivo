#!/usr/bin/env bash
# Lệnh lint chuẩn cho cả 2 phía.
set -euo pipefail
echo "== Backend checkstyle (nếu có cấu hình) =="
(cd backend && mvn -q -DskipTests compile)
echo "== Frontend lint =="
(cd frontend && npm run lint)
