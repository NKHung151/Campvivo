# Campvivo

Hệ thống TMĐT trang thiết bị dã ngoại — đồ án tốt nghiệp (Spring Boot + Next.js + PostgreSQL,
WebSocket, VNPay).

## Bắt đầu nhanh
- Đọc `AGENTS.md` trước (entrypoint cho mọi AI coding agent: Claude Code, Codex, Antigravity...).
- Yêu cầu gốc: báo cáo Phase1 (ngoài repo); ánh xạ use case → code: `docs/03-san-pham/use-cases.md`.
- Tài liệu dự án: `docs/` (xem bản đồ tài liệu trong `AGENTS.md` mục 4).
- Chạy local: `docs/04-van-hanh/local-development.md`.

## Cấu trúc thư mục
```
AGENTS.md          Entrypoint cho AI coding agent — ĐỌC TRƯỚC KHI CODE
.agents/            Skills, rules, workflows, plans cho AI
docs/               Phạm vi, FR/use case, kiến trúc, API, DB, UI — nguồn sự thật của dự án
backend/            Spring Boot (REST API)
frontend/           Next.js
infra/              Docker, nginx
tests/              e2e, integration, fixtures
```

## Nhóm
4 người — phân công chi tiết trong `AGENTS.md` mục 9 và `docs/01-tong-quan/project-overview.md`.
