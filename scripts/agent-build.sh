#!/usr/bin/env bash
# Lệnh build chuẩn — mọi agent/tool gọi script này thay vì tự đoán lệnh build.
set -euo pipefail
echo "== Backend build =="
(cd backend && mvn -q -DskipTests package)
echo "== Frontend build =="
(cd frontend && npm run build)
