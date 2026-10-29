# Plan: Chatbot AI RAG (FR-28) — Spring AI + PostgreSQL/pgvector + OpenAI

- **Ngày tạo:** 2026-10-25
- **Người/agent phụ trách:** Nguyen Nhu Khanh (+ Claude Code)
- **Tier:** 3 (FR-28) — được yêu cầu rõ ràng ngày 2026-10-25
- **Nhánh:** `feature/chatbot-rag` (tách từ `dev` @ `19438df`)
- **Module backend liên quan:** mới `chatbot-service/` (package `vn.campvivo.chatbot`); chỉ ĐỌC bảng của `catalog`,
  `combo`, `user`. Giai đoạn 0 sửa `backend/pom.xml`, `CampvivoApplication`, test support.
- **Module/feature frontend liên quan:** mới `components/features/chatbot/` (widget), `components/admin/chatbot/`
  (trang tài liệu), `services/chatbotService.ts`, `services/admin/adminChatbotService.ts`.

## Mục tiêu
Trợ lý ảo cho **khách hàng đã đăng nhập (role CUSTOMER)**:
1. Trả lời theo **tài liệu admin quản lý** (Word/PDF/TXT/MD) trên trang admin riêng — có trích nguồn.
2. **Tự truy vấn DB** (qua tool có tham số, không text-to-SQL) để trả lời về sản phẩm: theo tầm giá, danh mục,
   còn hàng, so sánh.
3. **Tư vấn theo tình huống** (số người, ngân sách, loại chuyến, số đêm): chọn combo khớp nhất trong số combo admin
   dựng trong DB (`product_combos` + `combo_items`), thiếu thì đề xuất combo + vài sản phẩm lẻ bổ sung, không vượt
   ngân sách. Mức phù hợp của combo được đánh giá **ngay lúc khách hỏi** từ dữ liệu DB, không lưu trước.

Chạy **độc lập** (`chatbot-service`, cổng 8090) nhưng dùng **chung DB `campvivo`** và chung `JWT_SECRET`; code viết
như một module backend để sau gộp vào `backend/` (task CB-30).

## Giả định đã chốt (đổi được — sửa plan nếu nhóm quyết khác)
| # | Câu hỏi | Giả định đang dùng |
|---|---|---|
| G1 | Độc lập hay module backend? | Service riêng dùng chung DB → gộp vào backend ở CB-30 |
| G2 | Lưu file tài liệu ở đâu? | `BYTEA` trong DB, ≤ 10MB/file |
| G3 | Combo phù hợp tình huống nào | **Phương án A (chốt 2026-10-25):** combo lấy từ DB; mỗi lần khách hỏi, `ComboFitAnalyzer` đọc combo + thành phần + tên/mô tả để đánh giá (số người, loại chuyến, mùa, thiếu gì). Không bảng phụ, không trang admin riêng — admin muốn bot hiểu đúng thì sửa tên/mô tả combo |
| G4 | Ai quản lý tài liệu | Chỉ ADMIN |
| G5 | DB local | Docker `pgvector/pgvector:pg16` (PostgreSQL 16 + pgvector) |
| G6 | Phiên bản | Spring Boot **3.5.16**, Spring AI **1.1.8**, springdoc **2.8.17** (đã chạy thử 123/123 test) |
| G7 | Model | Chat: model "mini" của OpenAI (đặt trong `spring.ai.openai.chat.options.model`); embedding: `text-embedding-3-small` (1536 chiều) |

## Phạm vi (trong/ngoài)
- **Trong phạm vi:** nâng Boot + pgvector + sửa lỗi time zone (Giai đoạn 0); chatbot-service (RAG tài liệu, tool sản
  phẩm/combo, tư vấn chuyến đi, hội thoại lưu DB, SSE); trang admin tài liệu; widget chat storefront;
  bộ đánh giá; docs.
- **Ngoài phạm vi:** bot thêm giỏ/đặt hàng thay khách; chuyển sang nhân viên (FR-26); giá sau chiến dịch (chưa có
  `PricingService` — dùng `products.price`, nối ở CB-30); OCR PDF scan; Spring Boot 4 / Spring AI 2.0.

## Quy ước cho mọi task
- Mỗi task = 1 hoặc vài commit Conventional Commits trên `feature/chatbot-rag` (vd. `feat(chatbot): ...`).
  Giai đoạn 0 (CB-01 → CB-03) commit tách riêng để có thể tách PR riêng vào `dev` nếu nhóm muốn duyệt trước.
- **Done** = code + test (happy path + ≥ 1 nhánh lỗi) + `./scripts/agent-lint.sh` & `./scripts/agent-test.sh` xanh
  + tick ô trong plan + 1 dòng `.agents/progress.md` khi xong một giai đoạn.
- Test chatbot KHÔNG gọi OpenAI thật: dùng `FakeChatModel` / `FakeEmbeddingModel` (CB-08). Chỉ CB-27 (đánh giá)
  gọi thật, chạy tay.
- Đổi API/bảng → sửa docs TRƯỚC (CB-04), code theo docs.

---

## Giai đoạn 0 — Nền móng backend (PR nhỏ, ảnh hưởng cả nhóm)

### CB-00 — Tạo nhánh + plan ✅
- Làm: `git switch -c feature/chatbot-rag origin/dev`, bỏ upstream `origin/dev` (push lần đầu bằng
  `git push -u origin feature/chatbot-rag`); viết plan này.
- File: `.agents/plans/active/chatbot-rag.md` (tạo).

### CB-01 — Sửa lỗi time zone `Asia/Saigon` (lỗi có sẵn, độc lập với chatbot)
- Bối cảnh: image `postgres:16` mới (Debian 13) bỏ alias `Asia/Saigon`; JVM trên Windows gửi alias này → Flyway/
  JDBC báo `FATAL: invalid value for parameter "TimeZone"` → 73/123 test lỗi, backend không chạy.
- Làm:
  - `CampvivoApplication.main`: `TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))` trước `run`.
  - `pom.xml`: `maven-surefire-plugin` `<argLine>-Duser.timezone=Asia/Ho_Chi_Minh</argLine>`;
    `spring-boot-maven-plugin` `<jvmArguments>-Duser.timezone=Asia/Ho_Chi_Minh</jvmArguments>`.
  - Docs: thêm **KI-07** (`known-issues.md`), mục lỗi trong `troubleshooting.md`.
- File: `backend/src/main/java/vn/campvivo/CampvivoApplication.java`, `backend/pom.xml`,
  `docs/99-ghi-chu/known-issues.md`, `docs/04-van-hanh/troubleshooting.md`.
- Kiểm tra: `docker pull postgres:16` mới nhất → `./scripts/agent-test.sh` 123/123.

### CB-02 — Nâng Spring Boot 3.3.0 → 3.5.16, springdoc 2.6.0 → 2.8.17
- Làm: đổi 2 version trong `pom.xml`; thay 4 chỗ `Specification.where(...)` (deprecated) bằng
  `Specification.allOf(...)`; ghi quyết định **D-27** (lý do: Spring AI 1.x cần Boot 3.5; danh sách thay đổi
  3.4/3.5 đã đối chiếu, không ảnh hưởng); cập nhật bảng phiên bản.
- File: `backend/pom.xml`, `backend/src/main/java/vn/campvivo/catalog/ProductService.java`,
  `.../catalog/admin/AdminProductService.java`, `.../combo/ComboService.java`, `.../combo/admin/AdminComboService.java`,
  `docs/02-ky-thuat/tech-stack.md`, `docs/99-ghi-chu/decisions.md`.
- Kiểm tra: 123/123 test; chạy app → `/swagger-ui/index.html` 200, `/v3/api-docs` 200; build không còn warning
  deprecation.

### CB-03 — PostgreSQL có pgvector (`pgvector/pgvector:pg16`)
- Làm: đổi image ở compose, Testcontainers, CI; hướng dẫn chuyển DB local (máy đang chạy PostgreSQL 18 cài thẳng:
  chuyển sang Docker, chạy lại Flyway + seed, chèn lại tài khoản mẫu theo `local-development.md`).
  **Cần nhóm đồng ý** (đụng compose chung).
- File: `docker-compose.yml`, `backend/src/test/java/vn/campvivo/support/PostgresTestContainerConfig.java`
  (`DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")`),
  `.github/workflows/backend-ci.yml` (service image), `docs/04-van-hanh/local-development.md`,
  `docs/04-van-hanh/docker.md`, `docs/02-ky-thuat/tech-stack.md`.
- Kiểm tra: `docker compose up -d` → `psql -c "create extension vector"` OK; 123/123 test; CI xanh.

---

## Giai đoạn 1 — Contract (docs trước code)

### CB-04 — Đặc tả chatbot trong docs
- Làm:
  - **D-28** (`decisions.md`): kiến trúc chatbot-service dùng chung DB, chỉ đọc bảng nghiệp vụ, bảng riêng
    `chatbot_*`, Flyway riêng; lộ trình gộp; không text-to-SQL.
  - `database.md`: mục mới "3.24 Bảng chatbot" (DDL ở **Phụ lục A**), ghi rõ không thuộc 22 bảng báo cáo.
  - `api.md`: mục **4.12 chatbot** (endpoint ở **Phụ lục B**), mã lỗi mới, kiểu TS, định dạng sự kiện SSE.
  - `pages.md`: route `/admin/chatbot/documents`, `/admin/chatbot/documents/[id]`; widget chat trên storefront.
  - `features.md`: FR-28 → "đang làm", luồng chính + ngoại lệ (chưa đăng nhập, hết lượt, không có tài liệu…).
  - `architecture.md` (sơ đồ có chatbot-service + OpenAI), `tech-stack.md` (Spring AI, biến môi trường
    `OPENAI_API_KEY`, `app.chatbot.*`).
- File: 7 file docs kể trên.
- Kiểm tra: tự đọc lại — mọi endpoint/bảng/mã lỗi dùng ở các task sau đều có trong docs.

---

## Giai đoạn 2 — Khung chatbot-service

### CB-05 — Tạo project Spring Boot `chatbot-service/`
- Làm: Maven project Boot 3.5.16 + `spring-ai-bom` 1.1.8 (starter-model-openai, tika-document-reader,
  pdf-document-reader), web, jdbc, validation, security, actuator, flyway(+postgresql), jjwt, owasp sanitizer,
  springdoc, testcontainers. Cổng 8090. Common chép từ backend giữ nguyên format lỗi.
  `ChatbotProperties` (`app.chatbot.*`: giới hạn tin nhắn, rate limit, topK, ngưỡng tương đồng, kích thước file,
  CORS, JWT secret).
- File (tạo):
  - `chatbot-service/pom.xml`, `chatbot-service/README.md`
  - `chatbot-service/src/main/java/vn/campvivo/chatbot/ChatbotApplication.java` (đặt time zone như CB-01)
  - `.../chatbot/common/dto/{ApiResponse,ApiErrorResponse,PageResponse}.java`
  - `.../chatbot/common/exception/{ErrorCode,BusinessException,GlobalExceptionHandler}.java`
  - `.../chatbot/common/config/{ChatbotProperties,ClockConfig,RequestIdFilter}.java`
  - `chatbot-service/src/main/resources/application.yml`, `application-dev.yml.example`
- File (sửa): `.gitignore` (bỏ qua `chatbot-service/src/main/resources/application-dev.yml`),
  `.env.example` (`OPENAI_API_KEY=`).
- Kiểm tra: `mvn -q test` (context load); chạy app → `GET :8090/actuator/health` UP.

### CB-06 — Flyway riêng + bảng chatbot + DB user chỉ đọc
- Làm: `spring.flyway.table=flyway_schema_history_chatbot`, `locations=classpath:db/chatbot`,
  `baseline-on-migrate=true`; migration theo Phụ lục A; script tạo role `campvivo_chatbot` (SELECT trên bảng nghiệp
  vụ, toàn quyền trên `chatbot_*`) để trong docs, không chạy tự động. Test chạy migration **thật của backend**
  (khai báo `testResources` trỏ `../backend/src/main/resources/db`) + seed V3/V4 + migration chatbot.
- File (tạo): `chatbot-service/src/main/resources/db/chatbot/V1__chatbot_extensions.sql`,
  `V2__chatbot_tables.sql`; `chatbot-service/src/test/java/vn/campvivo/chatbot/support/PgvectorTestContainer.java`;
  `.../ChatbotSchemaIT.java`; `docs/04-van-hanh/local-development.md` (mục role chatbot).
- Kiểm tra: IT: đủ bảng, extension `vector`/`pg_trgm`/`unaccent`; chạy lại không lỗi; backend vẫn
  `ddl-auto=validate` OK (không đụng bảng của backend).

### CB-07 — Bảo mật: JWT dùng chung, chỉ CUSTOMER chat
- Làm: chép logic `JwtAuthenticationFilter` của backend (kiểm chữ ký `JWT_SECRET`, đọc `users` bằng `JdbcTemplate`:
  `is_active`, `token_version`); `SecurityConfig`: `/api/chatbot/**` → `CUSTOMER`, `/api/admin/chatbot/**` → `ADMIN`,
  health/swagger công khai; CORS `http://localhost:3000`, cho phép header `Authorization`.
- File (tạo): `.../chatbot/security/{JwtTokenParser,JwtAuthFilter,JsonSecurityErrorHandler,SecurityConfig,
  CurrentUser}.java`; test `.../security/ChatbotSecurityIT.java`.
- Kiểm tra: không token → 401 `UNAUTHORIZED`; ADMIN gọi API chat → 403; CUSTOMER gọi admin → 403; tăng
  `token_version` (đăng xuất ở backend) → token cũ 401; user `is_active=false` → 401.

### CB-08 — Cấu hình Spring AI + model giả cho test
- Làm: `ChatClient.Builder` (temperature 0.3, max tokens), `EmbeddingModel` OpenAI; prompt hệ thống tiếng Việt dạng
  template; `FakeChatModel` (kịch bản trả lời/tool call cố định), `FakeEmbeddingModel` (vector tất định theo hash
  từ khoá) cho test; bật profile `test` không cần API key.
- File (tạo): `.../chatbot/ai/AiConfig.java`, `src/main/resources/prompts/system-vi.st`,
  `src/test/java/.../support/{FakeChatModel,FakeEmbeddingModel,TestAiConfig}.java`.
- Kiểm tra: test context load không cần `OPENAI_API_KEY`; chạy tay với key thật: gọi thử 1 câu qua
  `ChatClient` (log ra console).

### CB-09 — Script chuẩn + CI cho chatbot-service
- File (sửa): `scripts/agent-build.sh`, `scripts/agent-lint.sh`, `scripts/agent-test.sh` (thêm
  `(cd chatbot-service && mvn ...)`); (tạo) `.github/workflows/chatbot-ci.yml` (paths `chatbot-service/**`,
  Testcontainers trên ubuntu).
- Kiểm tra: 3 script chạy cả backend, chatbot-service, frontend; CI xanh trên PR.

---

## Giai đoạn 3 — Đọc catalog + tool sản phẩm/combo

### CB-10 — `CatalogReadPort` + `JdbcCatalogReader`
- Làm: interface port (để CB-30 thay bằng service backend) + cài đặt JDBC. Truy vấn:
  `searchProducts(ProductFilter)` (is_active, giá min/max, danh mục theo `categories.path LIKE '/9/58/%'`, brand,
  còn hàng = `stock_quantity - reserved_quantity > 0`, sort giá/tên/mới), `getProduct(slug)` (kèm `specs`, ảnh chính),
  `getProducts(slugs)`, `categoryTree()`, `searchCombos(...)`, `getCombo(slug)` (thành phần + số lượng + còn bộ),
  `listActiveCombos()`. Chỉ trả DTO.
- File (tạo): `.../chatbot/catalog/{CatalogReadPort,ProductFilter,ProductCard,ProductDetail,ComboCard,ComboDetail,
  CategoryNode}.java`, `.../catalog/jdbc/JdbcCatalogReader.java`; test `.../catalog/JdbcCatalogReaderIT.java`
  (trên seed V3/V4).
- Kiểm tra: lọc giá đúng biên; sản phẩm `is_active=false` không ra; combo `combo-ngu-am-mua-dong` (0 bộ) bị loại khi
  `inStockOnly`; danh mục cha lấy cả sản phẩm danh mục con.

### CB-11 — Embedding sản phẩm/combo + tìm kiếm lai
- Làm: `ItemEmbeddingJob` (chạy lúc khởi động + `@Scheduled` mỗi 10 phút): ghép text (tên, brand, danh mục,
  `short_description`, specs rút gọn; combo: tên, mô tả bỏ HTML, thành phần), băm `content_hash`, chỉ embed lại
  khi đổi, xoá dòng của mặt hàng đã xoá. Truy vấn lai: lọc cứng SQL + xếp hạng
  `0.7 * (1 - (embedding <=> :q)) + 0.3 * similarity(unaccent(lower(name)), unaccent(lower(:text)))`.
- File (tạo): `.../chatbot/embedding/{ItemEmbeddingJob,ItemTextBuilder,ItemEmbeddingRepository}.java`;
  sửa `JdbcCatalogReader` (nhận vector truy vấn); test `.../embedding/ItemEmbeddingJobIT.java`.
- Kiểm tra: lần 2 không gọi embedding (hash không đổi); sửa tên sản phẩm → embed lại đúng 1 dòng; tìm "leu cam
  trai" (không dấu) ra lều.

### CB-12 — Tool sản phẩm/combo + gom thẻ kết quả
- Làm: `@Tool` `searchProducts`, `getProductDetail`, `compareProducts`, `listCategories`, `searchCombos`,
  `getComboDetail`; mô tả tool + tham số bằng tiếng Việt; kết quả JSON gọn (không HTML, tiền là số nguyên VND);
  `ToolResultCollector` (theo request) gom `cards[]` gửi về client; giới hạn `limit ≤ 8`.
- File (tạo): `.../chatbot/tools/{CatalogTools,ComboTools,ToolResultCollector,ToolJson}.java`;
  test `.../tools/CatalogToolsTest.java`.
- Kiểm tra: gọi tool trực tiếp → JSON đúng schema; collector không trùng thẻ; slug sai → kết quả rỗng có thông
  báo, không ném lỗi ra model.

---

## Giai đoạn 4 — Tài liệu (RAG) + trang admin

### CB-13 — Lưu tài liệu + pipeline nạp
- Làm: `DocumentRepository` (JDBC); `IngestionService`: đọc bằng `TikaDocumentReader` (docx/txt/md) /
  `PagePdfDocumentReader` (pdf, giữ số trang) → làm sạch (bỏ khoảng trắng thừa, header/footer lặp) →
  `TokenTextSplitter` (~600 token, overlap ~80) → embed theo batch → **thay chunk cũ bằng chunk mới trong 1
  transaction** → `READY` + `chunk_count`. Chạy nền trên `applicationTaskExecutor`; lỗi → `FAILED` + `error_message`;
  không trích được chữ → `FAILED` "Không trích được chữ, cần file có text".
- File (tạo): `.../chatbot/knowledge/{KnowledgeDocument,DocumentStatus,DocumentRepository,ChunkRepository,
  IngestionService,TextCleaner}.java`; fixtures `src/test/resources/fixtures/{chinh-sach-doi-tra.docx,
  huong-dan-chon-leu.pdf,scan-only.pdf}`; test `.../knowledge/IngestionServiceIT.java`.
- Kiểm tra: docx + pdf → READY, có số trang trong metadata; pdf scan → FAILED; thay file → không còn chunk cũ.

### CB-14 — API admin tài liệu
- Làm: theo Phụ lục B (list/lọc/phân trang, upload multipart, chi tiết + 20 chunk đầu, sửa tiêu đề/mô tả/bật-tắt,
  thay file, reindex, xoá, tải file gốc). Kiểm tra định dạng theo MIME + đuôi file, ≤ 10MB, trùng `sha256` → 409.
- File (tạo): `.../chatbot/knowledge/admin/{AdminDocumentController,AdminDocumentService,DocumentRequests,
  DocumentResponses}.java`; sửa `ErrorCode` (`DOCUMENT_NOT_FOUND`, `DOCUMENT_DUPLICATE`,
  `UNSUPPORTED_FILE_TYPE`, `FILE_TOO_LARGE`, `DOCUMENT_PROCESSING`); test `.../AdminDocumentControllerTest.java`.
- Kiểm tra: từng mã lỗi đúng HTTP status; CUSTOMER/EMPLOYEE → 403; xoá → chunk mất (cascade).

### CB-15 — Truy hồi tài liệu + tool `searchKnowledge` + hỏi thử
- Làm: `KnowledgeSearch.search(query, topK, threshold)` chỉ lấy chunk của tài liệu `is_active AND status='READY'`
  (JOIN), trả nội dung + tên tài liệu + trang + điểm; `KnowledgeTools.searchKnowledge`;
  `POST /api/admin/chatbot/documents/test` (không gọi LLM).
- File (tạo): `.../chatbot/knowledge/{KnowledgeSearch,KnowledgeTools}.java`; sửa `AdminDocumentController`;
  test `.../KnowledgeSearchIT.java`.
- Kiểm tra: tắt tài liệu → không còn xuất hiện; dưới ngưỡng → rỗng; tool trả nguồn để bot trích dẫn.

### CB-16 — Frontend: trang admin "Tài liệu chatbot"
- Làm: client axios thứ 2 trỏ `NEXT_PUBLIC_CHATBOT_API_URL` dùng chung interceptor token/lỗi (tách hàm tạo client
  trong `apiClient.ts`); trang danh sách (bảng Ant Design: tên, loại, kích thước, số chunk, tag trạng thái, công tắc
  bật/tắt, sửa, thay file, xoá có xác nhận), modal tải lên (`Upload.Dragger`), trang chi tiết (thông tin, xem chunk,
  tải file gốc, ô hỏi thử). Tự hỏi lại trạng thái 3 giây/lần khi có tài liệu `PROCESSING`. Menu "Chatbot AI"
  (ADMIN_ONLY).
- File (tạo): `frontend/src/app/(admin)/admin/chatbot/documents/page.tsx`,
  `frontend/src/app/(admin)/admin/chatbot/documents/[id]/page.tsx`,
  `frontend/src/components/admin/chatbot/{DocumentListView,DocumentUploadModal,DocumentEditModal,DocumentDetail,
  RetrievalTester}.tsx`, `frontend/src/services/admin/adminChatbotService.ts`, `frontend/src/types/api/chatbot.ts`.
- File (sửa): `frontend/src/services/apiClient.ts`, `frontend/src/services/apiConfig.ts`,
  `frontend/src/lib/admin/nav.ts`, `frontend/.env.example`, `frontend/src/styles/admin/admin.css` (nếu cần).
- Kiểm tra: lint/tsc/build xanh; thử tay: tải docx → PROCESSING → READY; sửa, tắt, thay file, xoá; trạng thái
  loading/rỗng/lỗi; EMPLOYEE không thấy menu.

---

## Giai đoạn 5 — Tư vấn combo theo tình huống

### CB-17 — `ComboFitAnalyzer`: đánh giá combo ngay khi khách hỏi (phương án A)
- Nguyên tắc: combo là dữ liệu admin dựng trong DB (`product_combos`, `combo_items`); chatbot **không lưu** thông tin
  phụ nào về combo. Mỗi lần gọi, đọc combo đang bán + thành phần qua `CatalogReadPort` rồi tính:
  - **Số người phù hợp**: sức chứa lều theo tên/specs thành phần (regex `(\d+)\s*(-\s*(\d+))?\s*người`); số túi ngủ,
    đệm, ghế × `quantity`; lấy giá trị nhỏ nhất của các món "theo đầu người" → `peopleMin/peopleMax`. Không suy ra được
    → `null` (chỉ chấm theo tên/mô tả).
  - **Loại chuyến**: danh mục gốc của combo (D-23) + danh mục thành phần (`do-cam-trai` → `CAMPING`, `do-leo-nui` →
    `TREKKING_DAY`/`TREKKING_OVERNIGHT` tuỳ có túi ngủ/lều, `boi-lan` → `BEACH`) + từ khoá trong tên/mô tả combo
    ("trong ngày", "mùa đông", "cho bé"…).
  - **Mùa**: có thành phần thuộc `tui-ngu-mua-dong` hoặc mô tả có "mùa đông" → `WINTER`.
  - **Slot đã phủ**: thành phần thuộc slot nào của mẫu nhu cầu (CB-18), số lượng bao nhiêu.
  - Kết quả là record `ComboFit` chỉ sống trong request, không ghi DB, không có API.
- File (tạo): `.../chatbot/advisor/{ComboFit,ComboFitAnalyzer,TripType,Season}.java`; test
  `.../advisor/ComboFitAnalyzerTest.java` (dữ liệu 5 combo seed V4 dựng sẵn trong test),
  `.../advisor/ComboFitAnalyzerIT.java` (đọc thật từ seed).
- Kiểm tra: `combo-cam-trai-1-nguoi-co-ban` → 1 người, `CAMPING`; `combo-leo-nui-trong-ngay` → 1 người,
  `TREKKING_DAY`; `combo-boi-cho-be` → `BEACH`; `combo-ngu-am-mua-dong` → `WINTER`; admin đổi thành phần combo trong
  DB → lần gọi sau ra kết quả mới (không cache).

### CB-18 — Mẫu nhu cầu + `TripAdvisorService`
- Làm: `trip-templates.yml` mỗi loại chuyến gồm các slot (danh mục thật trong seed V3, quy tắc số lượng, bắt buộc/
  nên có, tỷ trọng ngân sách), ví dụ camping:
  `leu-trai` (sức chứa ≥ N), `tui-ngu` ×N (mùa đông → `tui-ngu-mua-dong`), `den-leu` ×1, `bep-ga-cam-trai` ×1 +
  `nhien-lieu-bep-da-ngoai`, `ghe-cam-trai` ×N, `tui-giu-lanh-tui-giu-nhiet` ×1; trekking: `balo-leo-nui` ×N,
  `gay-leo-nui-chong-shock` ×2N, đèn, áo mưa.
  Thuật toán (mục 2.4 của kế hoạch): (1) đọc combo đang bán, còn bộ từ DB; (2) `ComboFitAnalyzer` (CB-17) đánh giá
  từng combo, chấm điểm = phủ slot bắt buộc + khớp số người + khớp ngân sách + tương đồng ngữ nghĩa giữa nhu cầu
  và tên/mô tả combo (embedding CB-11); (3) top 3; (4) phân tích thiếu (số lượng / slot) → tìm sản phẩm lẻ trong ngân
  sách còn lại, ưu tiên còn hàng, phù hợp; cân nhắc nhân combo; (5) không combo nào phủ ≥ 40% → ráp toàn bộ bằng
  sản phẩm lẻ; (6) trả `TripPlan[]` (combo?, addOns[], tổng tiền, tiền dư, thiếu bao nhiêu, lý do từng món).
  Toàn bộ là Java thuần, nhận dữ liệu qua `CatalogReadPort` → test được không cần LLM.
- File (tạo): `chatbot-service/src/main/resources/chatbot/trip-templates.yml`,
  `.../chatbot/advisor/{TripNeed,TripPlan,TripTemplateProperties,TripSlot,ComboScorer,GapFiller,
  TripAdvisorService}.java`; test `.../advisor/TripAdvisorServiceTest.java`, `.../advisor/TripAdvisorServiceIT.java`.
- Kiểm tra (unit): tổng ≤ ngân sách (trừ khi báo thiếu); đủ số lượng cho N người; không chọn hàng hết; ngân sách
  quá thấp → báo mức tối thiểu; "1 người, 6 triệu, cắm trại" → combo 1 người + bếp; "3 người, 10 triệu" → combo +
  thêm 2 túi ngủ/2 ghế hoặc lều lớn hơn.

### CB-19 — Tool `recommendForTrip` + luật hỏi lại
- Làm: `AdvisorTools.recommendForTrip(people, budget, tripType, nights, season?, alreadyHave[]?, mustHave[]?)`
  → gọi `TripAdvisorService`, đưa combo/sản phẩm vào `ToolResultCollector`; thêm luật vào `system-vi.st`: thiếu số
  người hoặc ngân sách thì hỏi lại, không đoán; luôn nói rõ phần nào là combo, phần nào mua thêm.
- File (tạo): `.../chatbot/tools/AdvisorTools.java`; sửa `prompts/system-vi.st`; test `.../tools/AdvisorToolsTest.java`.
- Kiểm tra: tham số thiếu/không hợp lệ (people ≤ 0, budget ≤ 0) → kết quả báo lỗi để model hỏi lại.

### CB-20 — ~~Trang admin "Hồ sơ combo"~~ — **đã bỏ** (phương án A, 2026-10-25)
- Không còn bảng `chatbot_combo_profiles` nên không cần trang này. Admin quản lý combo ở trang `/admin/combos` có sẵn;
  muốn bot đánh giá đúng thì đặt tên/mô tả combo rõ (vd. "cho 2 người", "cắm trại qua đêm", "mùa đông").

---

## Giai đoạn 6 — Hội thoại

### CB-21 — Hội thoại + bộ nhớ trên DB
- Làm: `ConversationService` (tạo, liệt kê theo user, xem, xoá — người khác → 404 `CONVERSATION_NOT_FOUND`);
  `JdbcChatMemoryRepository` cài `ChatMemoryRepository` của Spring AI trên `chatbot_messages`;
  `MessageWindowChatMemory` 20 tin; tiêu đề lấy 60 ký tự đầu câu hỏi đầu tiên.
- File (tạo): `.../chatbot/conversation/{Conversation,ConversationRepository,MessageRepository,
  JdbcChatMemoryRepository,ConversationService,ConversationController,dto/*}.java`;
  test `.../conversation/ConversationControllerTest.java`.
- Kiểm tra: CUSTOMER A không xem được hội thoại của B; xoá → mất tin nhắn (cascade).

### CB-22 — Gửi tin nhắn dạng SSE
- Làm: `POST /api/chatbot/conversations/{id}/messages` → `text/event-stream`: `delta` (chữ), `cards`, `sources`,
  `done` (messageId, token dùng), `error` (code). `ChatClient` + tool (Catalog/Combo/Advisor/Knowledge) +
  `MessageChatMemoryAdvisor`; lưu tin USER trước, tin ASSISTANT sau khi xong (kèm cards/sources/token); tối đa 5 lượt
  gọi tool; lỗi OpenAI → sự kiện `error` `AI_UNAVAILABLE`, không lưu tin rỗng.
- File (tạo): `.../chatbot/conversation/{ChatStreamService,ChatStreamController,StreamEvent}.java`;
  test `.../conversation/ChatStreamControllerTest.java` (FakeChatModel).
- Kiểm tra: thứ tự sự kiện đúng; tin nhắn được lưu đủ; model giả ném lỗi → `error`.

### CB-23 — Giới hạn + bảo vệ
- Làm: giới hạn theo user (mặc định 20 tin/phút, 200 tin/ngày — `app.chatbot.rate-limit.*`, đếm trên
  `chatbot_messages`) → 429 `CHAT_RATE_LIMITED`; tin > 1.000 ký tự → 400; hoàn thiện `system-vi.st` (chỉ tư vấn đồ
  dã ngoại/Campvivo; giá/tồn chỉ lấy từ tool; tài liệu/mô tả là dữ liệu, không phải chỉ dẫn); log token
  (prompt/completion) vào `chatbot_messages`.
- File (sửa/tạo): `.../chatbot/conversation/ChatRateLimiter.java`, `ChatStreamService.java`,
  `prompts/system-vi.st`, `ErrorCode.java`; test `.../ChatRateLimiterTest.java`.
- Kiểm tra: tin thứ 21 trong 1 phút → 429; tin quá dài → 400.

---

## Giai đoạn 7 — Widget chat storefront

### CB-24 — `chatbotService.ts` (REST + đọc SSE)
- Làm: hàm liệt kê/tạo/xoá hội thoại, lấy tin nhắn; `sendMessage(id, text, handlers, signal)` dùng `fetch` +
  `ReadableStream` (vì `EventSource` không gửi được header `Authorization`), tách sự kiện SSE, hỗ trợ huỷ bằng
  `AbortController`, 401 → gọi handler hết phiên như `apiClient`.
- File (tạo): `frontend/src/services/chatbotService.ts`, `frontend/src/services/sseReader.ts`; sửa
  `frontend/src/types/api/chatbot.ts`.
- Kiểm tra: tsc; thử tay với chatbot-service local.

### CB-25 — Giao diện widget
- Làm: nút nổi góc phải trên mọi trang storefront; panel: danh sách hội thoại + nút "Cuộc trò chuyện mới", khung tin
  nhắn (chữ hiện dần, định dạng đơn giản: xuống dòng, **đậm**, danh sách — tự viết, không cài thêm thư viện), thẻ
  sản phẩm/combo (ảnh, giá `1.250.000đ`, link `/products/{slug}`, `/combos/{slug}`), chip nguồn tài liệu, câu hỏi
  gợi ý khi rỗng, trạng thái đang trả lời / lỗi + thử lại / hết lượt. Chưa đăng nhập → "Đăng nhập để chat với trợ lý"
  + nút đăng nhập; role khác CUSTOMER → ẩn widget. Responsive (mobile: panel toàn màn hình).
- File (tạo): `frontend/src/components/features/chatbot/{ChatLauncher,ChatPanel,ConversationList,MessageList,
  ChatMessage,ChatInput,ProductCardMini,ComboCardMini,SourceChips,SuggestedQuestions}.tsx`,
  `frontend/src/components/features/chatbot/formatMessage.ts`, `frontend/src/styles/shop/chatbot.css`.
- File (sửa): `frontend/src/app/(store)/layout.tsx` (gắn `ChatLauncher`, import CSS).
- Kiểm tra: lint/tsc/build; thử tay 3 vai trò (khách vãng lai, CUSTOMER, ADMIN); màn hình hẹp; mất mạng giữa
  chừng.

---

## Giai đoạn 8 — Đánh giá, tài liệu, PR

### CB-26 — Tài liệu mẫu để demo
- Làm: soạn 3–5 tài liệu mẫu của Campvivo (chính sách đổi trả/bảo hành/giao hàng theo `app.*` và docs; hướng dẫn chọn
  lều, túi ngủ; checklist cắm trại) dạng docx/pdf — nội dung tự viết, không chép của cửa hàng khác.
- File (tạo): `chatbot-service/sample-docs/*.docx|pdf` (+ `README.md` mô tả).
- Kiểm tra: nạp qua trang admin → READY.

### CB-27 — Bộ câu hỏi đánh giá + chạy đánh giá
- Làm: ~40 câu (15 tài liệu, 15 sản phẩm, 10 tư vấn combo) kèm kỳ vọng; runner profile `eval` gọi OpenAI thật:
  Spring AI `RelevancyEvaluator` + `FactCheckingEvaluator` cho câu tài liệu; kiểm tự động "mọi giá/slug trong câu
  trả lời có trong DB"; tổng hợp điểm, độ trễ, token, chi phí ước tính.
- File (tạo): `chatbot-service/src/test/resources/eval/questions.yml`,
  `chatbot-service/src/test/java/vn/campvivo/chatbot/eval/{EvalRunner,PriceGroundingChecker}.java`,
  `chatbot-service/eval/report-<ngày>.md` (kết quả, dùng cho báo cáo).
- Kiểm tra: chạy tay `mvn -Peval test`; ghi kết quả.

### CB-28 — Postman + đồng bộ docs + progress
- File (sửa/tạo): `tests/api/campvivo.postman_collection.json` (thư mục "chatbot", có request lỗi),
  rà lại docs CB-04 cho khớp code, `.agents/progress.md`, tick plan.
- Kiểm tra: `agent-lint`, `agent-test` xanh.

### CB-29 — Mở PR vào `dev`
- Làm: push `feature/chatbot-rag`, mở PR theo `.github/pull_request_template.md` (Giai đoạn 0 có thể là PR riêng
  trước); reviewer: chủ module catalog/combo (Người 2), auth (Người 3). Không tự merge.

---

## Giai đoạn 9 — Gộp vào backend (làm sau, khi nhóm sẵn sàng)

### CB-30 — Chuyển chatbot thành module `backend/.../chatbot`
- Làm: chuyển package `vn.campvivo.chatbot` sang `backend/src/main/java/vn/campvivo/chatbot/`; dependency Spring AI
  vào `backend/pom.xml`; migration chatbot → `backend/src/main/resources/db/migration/V<n>__chatbot.sql` (chuyển
  lịch sử Flyway: baseline hoặc giữ bảng đã có); xoá `security/` và `common/` chép tạm, thêm luật vào
  `auth/SecurityConfig.java`; thay `JdbcCatalogReader` bằng adapter gọi service public của `catalog`/`combo` (cần
  Người 2 thêm hàm truy vấn) và `PricingService` khi có; frontend đổi `NEXT_PUBLIC_CHATBOT_API_URL` → `API_BASE_URL`;
  xoá `chatbot-service/`, `chatbot-ci.yml`; cập nhật D-28, architecture.
- Kiểm tra: toàn bộ test chatbot chạy trong backend; demo end-to-end lại.

---

## Phụ lục A — DDL dự kiến (`db/chatbot/`)

```sql
-- V1__chatbot_extensions.sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- V2__chatbot_tables.sql
CREATE TABLE chatbot_documents (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title         VARCHAR(255)  NOT NULL,
    description   VARCHAR(1000),
    file_name     VARCHAR(255)  NOT NULL,
    content_type  VARCHAR(100)  NOT NULL,
    size_bytes    BIGINT        NOT NULL CHECK (size_bytes > 0),
    sha256        CHAR(64)      NOT NULL UNIQUE,
    content       BYTEA         NOT NULL,
    status        VARCHAR(30)   NOT NULL CHECK (status IN ('PROCESSING', 'READY', 'FAILED')),
    error_message VARCHAR(1000),
    chunk_count   INT           NOT NULL DEFAULT 0,
    is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by    BIGINT        NOT NULL REFERENCES users (id),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE chatbot_document_chunks (
    id          UUID PRIMARY KEY,
    document_id BIGINT       NOT NULL REFERENCES chatbot_documents (id) ON DELETE CASCADE,
    chunk_index INT          NOT NULL,
    content     TEXT         NOT NULL,
    metadata    JSONB        NOT NULL DEFAULT '{}'::jsonb,   -- {"page": 3, "heading": "..."}
    embedding   vector(1536) NOT NULL,
    CONSTRAINT uq_chatbot_chunks_doc_index UNIQUE (document_id, chunk_index)
);
CREATE INDEX idx_chatbot_chunks_embedding ON chatbot_document_chunks USING hnsw (embedding vector_cosine_ops);

-- Embedding sản phẩm/combo cho tìm kiếm ngữ nghĩa (không FK để xoá mặt hàng không bị chặn; job dọn dòng mồ côi)
CREATE TABLE chatbot_item_embeddings (
    item_type    VARCHAR(10)  NOT NULL CHECK (item_type IN ('PRODUCT', 'COMBO')),
    item_id      BIGINT       NOT NULL,
    content_hash CHAR(64)     NOT NULL,
    embedding    vector(1536) NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (item_type, item_id)
);
CREATE INDEX idx_chatbot_item_embeddings ON chatbot_item_embeddings USING hnsw (embedding vector_cosine_ops);

CREATE TABLE chatbot_conversations (
    id         UUID PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title      VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_chatbot_conversations_user ON chatbot_conversations (user_id, updated_at DESC);

CREATE TABLE chatbot_messages (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id   UUID          NOT NULL REFERENCES chatbot_conversations (id) ON DELETE CASCADE,
    role              VARCHAR(10)   NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content           TEXT          NOT NULL,
    cards             JSONB         NOT NULL DEFAULT '[]'::jsonb,
    sources           JSONB         NOT NULL DEFAULT '[]'::jsonb,
    prompt_tokens     INT,
    completion_tokens INT,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_chatbot_messages_conversation ON chatbot_messages (conversation_id, created_at);
```

## Phụ lục B — Endpoint dự kiến (chi tiết ghi ở `api.md` 4.12, CB-04)

| Method | Path | Quyền | Ghi chú |
|---|---|---|---|
| GET | `/api/chatbot/conversations` | CUSTOMER | Hội thoại của chính mình, mới nhất trước |
| POST | `/api/chatbot/conversations` | CUSTOMER | Tạo hội thoại rỗng |
| GET | `/api/chatbot/conversations/{id}/messages` | CUSTOMER (chủ) | Người khác → 404 |
| DELETE | `/api/chatbot/conversations/{id}` | CUSTOMER (chủ) | |
| POST | `/api/chatbot/conversations/{id}/messages` | CUSTOMER (chủ) | Body `{content}`; trả SSE `delta/cards/sources/done/error`; 429 khi vượt giới hạn |
| GET | `/api/admin/chatbot/documents` | ADMIN | `?q=&status=&active=&page=&limit=` |
| POST | `/api/admin/chatbot/documents` | ADMIN | multipart `file`, `title`, `description?` |
| GET | `/api/admin/chatbot/documents/{id}` | ADMIN | + 20 chunk đầu |
| PUT | `/api/admin/chatbot/documents/{id}` | ADMIN | `{title, description, active}` |
| PUT | `/api/admin/chatbot/documents/{id}/file` | ADMIN | multipart `file` — nạp lại |
| POST | `/api/admin/chatbot/documents/{id}/reindex` | ADMIN | |
| DELETE | `/api/admin/chatbot/documents/{id}` | ADMIN | |
| GET | `/api/admin/chatbot/documents/{id}/file` | ADMIN | Tải file gốc |
| POST | `/api/admin/chatbot/documents/test` | ADMIN | `{question}` → chunk + điểm, không gọi LLM |

Không có endpoint riêng cho combo: tư vấn combo chạy bên trong tool `recommendForTrip` khi khách chat.

Mã lỗi mới: `DOCUMENT_NOT_FOUND` (404), `DOCUMENT_DUPLICATE` (409), `UNSUPPORTED_FILE_TYPE` (400),
`FILE_TOO_LARGE` (400), `DOCUMENT_PROCESSING` (409), `CONVERSATION_NOT_FOUND` (404), `CHAT_RATE_LIMITED` (429),
`AI_UNAVAILABLE` (503).

## Ảnh hưởng tới hệ thống hiện có
- Giai đoạn 0 đụng mọi thành viên: phiên bản Boot/springdoc, image PostgreSQL (dữ liệu volume giữ nguyên vì cùng
  PostgreSQL 16), sửa time zone. Đã chạy thử: 123/123 test trên Boot 3.5.16 với `postgres:16`, `pgvector:pg16`,
  `pgvector:pg18`; app chạy, Swagger 200, seed V1–V4 nạp đủ.
- Chatbot không ghi vào bảng nghiệp vụ. Phụ thuộc schema `products`, `product_combos`, `combo_items`, `categories`,
  `images`, `users` → đổi tên cột ở các bảng này phải báo, IT CB-10 sẽ bắt lỗi.
- Không đụng tồn kho, đơn hàng, thanh toán.

## Cách test
- Tự động: unit (advisor, tool, rate limit) + IT Testcontainers `pgvector/pgvector:pg16` chạy migration + seed thật
  của backend; model AI giả.
- Thủ công: kịch bản demo — đăng nhập CUSTOMER → hỏi chính sách (có nguồn) → "lều 2 người dưới 3 triệu" → "nhà mình
  4 người đi cắm trại 2 đêm, tầm 10 triệu" → mở thẻ sản phẩm; admin tắt tài liệu → bot không còn trích.
- Đánh giá: CB-27.

## Trạng thái
- [x] CB-00 Nhánh + plan
- [x] CB-01 Time zone · [x] CB-02 Boot 3.5 · [x] CB-03 pgvector
- [x] CB-04 Docs/contract
- [x] CB-05 Khung · [x] CB-06 Flyway/bảng · [x] CB-07 Bảo mật · [x] CB-08 Spring AI · [x] CB-09 Script/CI
- [x] CB-10 Catalog reader · [x] CB-11 Embedding mặt hàng · [x] CB-12 Tool sản phẩm/combo
- [x] CB-13 Pipeline tài liệu · [x] CB-14 API admin tài liệu · [x] CB-15 Truy hồi · [x] CB-16 Trang admin tài liệu
- [x] CB-17 ComboFitAnalyzer · [x] CB-18 TripAdvisor · [x] CB-19 Tool tư vấn · ~~CB-20~~ (đã bỏ)
- [x] CB-21 Hội thoại · [x] CB-22 SSE · [x] CB-23 Giới hạn/bảo vệ
- [x] CB-24 chatbotService · [x] CB-25 Widget
- [x] CB-26 Tài liệu mẫu · [~] CB-27 Đánh giá (bộ câu hỏi + script xong; CHƯA chạy với OpenAI thật — chưa có key)
- [~] CB-28 docs + progress xong; Postman chưa làm (dùng Swagger :8090) · [ ] CB-29 PR (chưa commit/push theo yêu cầu)
- [ ] CB-30 Gộp vào backend (sau)

## Nhật ký thực hiện 2026-10-25 (khác plan / phát hiện thêm)
- **Chưa commit/push** (theo yêu cầu). Mọi thay đổi nằm ở working tree nhánh `feature/chatbot-rag`.
- CB-01: lỗi time zone đã có mục trong `troubleshooting.md`/`local-development.md` → sửa code (`main` + `argLine`) và cập
  nhật 2 mục đó, KHÔNG thêm KI mới; không cần `jvmArguments` vì `main()` tự đặt múi giờ.
- CB-02: KHÔNG sửa 4 service dùng `Specification.where` (module Người 2) — chỉ cảnh báo deprecation, ghi ở D-27.
- CB-04: dùng lại mã `NOT_FOUND` chung thay cho `DOCUMENT_NOT_FOUND`/`CONVERSATION_NOT_FOUND`; endpoint hỏi thử là
  `/search-test`; trường bật/tắt tên `isActive` (giống backend); client gửi `Accept: text/event-stream, application/json`.
- CB-05: thêm ghim `commons-compress` 1.28.0 (Testcontainers kéo 1.24 làm hỏng đọc .docx của Tika ở cả runtime);
  surefire chạy cả `*IT`.
- CB-12/CB-19: tool combo gộp vào `CatalogTools`; thẻ gửi về = thẻ đề xuất + thẻ được nhắc tên trong câu trả lời.
- CB-18: thêm `name-keywords` cho slot lều/túi ngủ/đèn/bếp — seed V3 có sản phẩm gắn nhầm danh mục ("Giày leo núi
  … 3A024A" nằm trong danh mục Túi ngủ) làm bot đề xuất giày thay túi ngủ.
- CB-21/22: không dùng `ChatMemoryRepository` của Spring AI; lịch sử lấy từ `chatbot_messages` (lưu kèm thẻ, nguồn, token).
- CB-16/24: KHÔNG sửa `apiClient.ts`; thêm `services/chatbotHttp.ts` (401 → hỏi `/users/me` qua apiClient để luồng hết
  phiên chung xử lý). Có sửa `lib/errorMessages.ts` (thêm mã chatbot).
- CB-25: widget gộp còn 4 component; nút chat đặt trên cụm nút Gọi/Zalo có sẵn (`.cv-widget`).
- Ngưỡng truy hồi mặc định hạ 0.5 → 0.3 (embedding OpenAI cho điểm cosine thấp; admin xem điểm ở "Hỏi thử").
- Kiểm chứng: backend 123/123 test; chatbot 48/48 test; frontend lint + tsc sạch. Chạy thật end-to-end (backend + chatbot
  + Next dev + OpenAI giả lập cục bộ trả `tool_calls`): đăng nhập thật → tư vấn chuyến đi ra combo + đồ mua thêm, tìm
  sản phẩm, trích nguồn tài liệu, admin tải/bật/tắt/hỏi thử tài liệu, giao diện điện thoại. Chưa thử với OpenAI thật.
- Lỗi có sẵn, ngoài phạm vi (chưa sửa, cần chủ module xử lý):
  - `npm run build` lỗi prerender ở nhánh `dev` (kiểm bằng bản `HEAD` chưa sửa): `services/serverApi.ts` bắt mọi lỗi
    của `fetch` kể cả lỗi "dynamic server usage" của Next → đổi thành `NETWORK_ERROR`.
  - `backend` surefire không chạy class `*IT` (vd. `OrderServiceIT` chưa bao giờ chạy trong `mvn test`).
  - Seed V3: sản phẩm gắn nhầm danh mục (ví dụ trên).
