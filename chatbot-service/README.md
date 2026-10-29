# chatbot-service — Chatbot AI Campvivo (FR-28)

Service Spring Boot riêng (cổng **8090**) dùng **chung database `campvivo`** với backend: chỉ đọc sản phẩm, combo, danh
mục, user; chỉ ghi các bảng `chatbot_*`. Quyết định và lý do: `docs/99-ghi-chu/decisions.md` D-27, D-28. API:
`docs/02-ky-thuat/api.md` mục 4.12. Bảng: `docs/02-ky-thuat/database.md` mục 3.24.

- Spring Boot 3.5.16, Spring AI 1.1.8 (OpenAI chat + `text-embedding-3-small`), PostgreSQL 16 + pgvector.
- Chat chỉ cho **CUSTOMER** (JWT do backend cấp, chung `JWT_SECRET`); quản lý tài liệu chỉ **ADMIN**.
- Model không viết SQL: chỉ gọi tool có tham số (`tools/`), giá/tồn kho luôn lấy từ DB. Tư vấn combo tính bằng code
  (`advisor/TripAdvisorService`), mẫu nhu cầu ở `src/main/resources/chatbot/trip-templates.yml`.

## Chạy local

1. **PostgreSQL có pgvector**: `docker compose up -d` ở thư mục gốc (image `pgvector/pgvector:pg16`).
2. **Backend chạy trước** (tạo bảng + seed): xem `docs/04-van-hanh/local-development.md`.
3. **Cấu hình**: copy `src/main/resources/application-dev.yml.example` thành `application-dev.yml` (không commit), điền:
   - `app.jwt.secret` — **giống hệt** `app.jwt.secret` của backend;
   - OpenAI key: thay `sk-your-key` ở `spring.ai.openai.api-key` trong `application-dev.yml` (file không được commit).
     Hoặc xoá dòng đó và dùng biến môi trường `OPENAI_API_KEY`. Spring Boot KHÔNG đọc file `.env`. Đổi model:
     `OPENAI_CHAT_MODEL`.
4. **Chạy**: `cd chatbot-service` rồi `mvn spring-boot:run` (lần đầu Flyway tạo bảng `chatbot_*`, lịch sử ở
   `flyway_schema_history_chatbot`). Swagger: http://localhost:8090/swagger-ui.html.
5. **Frontend**: `NEXT_PUBLIC_CHATBOT_API_URL=http://localhost:8090/api` (mặc định đã đúng). Đăng nhập tài khoản
   khách → nút "Hỏi trợ lý" góc phải storefront. Admin → menu **Chatbot AI → Tài liệu cho chatbot**.

Không có `OPENAI_API_KEY` app vẫn chạy: chat trả sự kiện lỗi `AI_UNAVAILABLE`, tài liệu tải lên báo `FAILED` (nạp lại
sau khi có key); tìm sản phẩm vẫn chạy bằng chữ.

## Tài liệu mẫu để demo

`sample-docs/` có 3 tài liệu Markdown (đổi trả/bảo hành, giao hàng/thanh toán, hướng dẫn chọn đồ) — nội dung MẪU khớp
cấu hình hiện tại; tải lên ở trang admin.

## Test

```bash
mvn test        # hoặc ./scripts/agent-test.sh ở thư mục gốc
```

Testcontainers (`pgvector/pgvector:pg16`, cần Docker) chạy **migration + seed thật của backend** rồi migration chatbot;
model AI là bản giả (`src/test/.../support/Fake*Model`) nên không cần key, không tốn tiền.

## Đánh giá với OpenAI thật

Khi backend + chatbot đang chạy có key và đã nạp `sample-docs/`:

```bash
node chatbot-service/eval/run-eval.mjs            # tài khoản mẫu customer@campvivo.local
```

30 câu (`eval/questions.json`: tư vấn chuyến đi, tra sản phẩm, hỏi tài liệu, hỏi lại khi thiếu thông tin, từ chối
ngoài phạm vi) → báo cáo `eval/report-<ngày>.md` (tỷ lệ đạt, thời gian, token, số tiền cần xem lại).

## Cấu trúc

```
src/main/java/vn/campvivo/chatbot/
  conversation/   hội thoại, gửi tin dạng SSE, giới hạn tần suất
  tools/          tool cho model: tìm sản phẩm/combo, tư vấn chuyến đi, tra tài liệu
  advisor/        đánh giá combo lúc hỏi (ComboFitAnalyzer) + chọn món trong ngân sách (TripAdvisorService)
  knowledge/      tài liệu: lưu, nạp nền (đọc PDF/DOCX/MD/TXT, chia đoạn, embed), truy hồi; admin/ = API admin
  catalog/        CatalogReadPort (chỉ đọc) + JdbcCatalogReader — khi gộp vào backend thay bằng service của catalog/combo
  embedding/      job embed sản phẩm/combo (chỉ embed lại khi đổi)
  security/       đọc JWT của backend (chép tạm logic JwtAuthenticationFilter — bỏ khi gộp)
  common/         ApiResponse, ErrorCode, GlobalExceptionHandler... (cùng format với backend)
```

Gộp vào backend sau này: `.agents/plans/active/chatbot-rag.md` task CB-30.
