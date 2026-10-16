# Coding Conventions

## Backend (Java 17 / Spring Boot 3)

**Cấu trúc & đặt tên**
- Package theo module nghiệp vụ `vn.campvivo.<module>` (xem `architecture.md`), DTO trong
  `<module>/dto`, sự kiện trong `<module>/event`.
- Entity số ít (`Product`), map bảng số nhiều bằng `@Table(name = "products")`. Repository
  `<Entity>Repository`, Service theo nghiệp vụ (`OrderService.createOrder`), Controller
  `<Module>Controller`; controller admin tách riêng `Admin<Module>Controller` (path `/api/admin/...`).
- DTO request: `<Action><Entity>Request` (record + Bean Validation). DTO response:
  `<Entity><View>Response` (record). Mapper viết tay (static method `from(entity)`) — không dùng
  MapStruct trừ khi nhóm thống nhất.
- Không expose Entity qua API. Không trả `null` cho danh sách — trả list rỗng.

**Entity / JPA**
- Khoá chính `@GeneratedValue(strategy = GenerationType.IDENTITY)`, kiểu `Long`.
- Tiền `BigDecimal` (scale 0). Không dùng `double`/`float` cho tiền. Tiện ích làm tròn trong
  `common/util/Money` (HALF_UP).
- Enum `@Enumerated(EnumType.STRING)`. Thời gian `OffsetDateTime` hoặc `Instant` (cột `timestamptz`).
- Quan hệ `@ManyToOne(fetch = FetchType.LAZY)`; tránh `@OneToMany` hai chiều nếu không cần. Không
  dùng `CascadeType.REMOVE` trên dữ liệu nghiệp vụ.
- `spring.jpa.open-in-view=false`; `ddl-auto=validate` (schema do Flyway quản lý).
- Truy vấn khoá: `@Lock(LockModeType.PESSIMISTIC_WRITE)` + `@Query` có `ORDER BY id` khi khoá nhiều dòng.
- Tránh N+1: dùng `JOIN FETCH` / `@EntityGraph` cho màn danh sách.

**Lỗi**
- Một enum `common/exception/ErrorCode` chứa mọi mã lỗi trong `api.md` mục 2 kèm `HttpStatus` và
  message mặc định tiếng Việt. Ném `new BusinessException(ErrorCode.OUT_OF_STOCK, details)`.
  (Lớp `BusinessException` hiện nhận `errorCode` + `status` dạng rời — chuẩn hoá sang enum khi
  viết module đầu tiên.)
- `GlobalExceptionHandler` chuyển `BusinessException`, `MethodArgumentNotValidException`
  (→ `VALIDATION_ERROR` + `fieldErrors`), `AccessDeniedException` (403), lỗi khác (500, log ERROR,
  message chung) sang format lỗi chuẩn.
- Không `catch` rỗng; không nuốt exception trong transaction.

**Transaction**
- `@Transactional` ở Service, không đặt ở Controller/Repository. Service chỉ đọc dùng
  `@Transactional(readOnly = true)`.
- Không gọi API ngoài (email, VNPay ngoài tạo URL) trong lúc giữ khoá DB lâu — email gửi sau commit.

**Cấu hình**
- Tham số nghiệp vụ đọc qua `@ConfigurationProperties(prefix = "app")` (bảng key trong
  `tech-stack.md`), không hardcode.

**Log**
- SLF4J. INFO cho sự kiện nghiệp vụ (tạo đơn, đổi trạng thái, IPN), WARN cho bất thường (IPN cho đơn
  đã huỷ), ERROR cho lỗi hệ thống. Không log mật khẩu, OTP, token, chữ ký/payload thanh toán đầy đủ.

## Frontend (Next.js 14 / TypeScript)
- Component function, PascalCase; hook bắt đầu bằng `use`; file component trùng tên component.
- Gọi API chỉ trong `services/<module>Service.ts` qua `apiClient`; trả Promise kiểu trong `types/`
  (khớp `api.md`). Component không gọi `fetch`/`axios` trực tiếp.
- Không dùng `any` nếu không có comment giải thích.
- Tiền hiển thị bằng helper `formatVnd(n)` → `450.000₫`. Ngày giờ hiển thị theo `vi-VN`.
- Hiển thị lỗi theo `errorCode` (bảng map message ở `lib/errorMessages.ts`), fallback `message`
  từ server; không hiện lỗi kỹ thuật thô.
- Mỗi trang dữ liệu động có đủ loading / empty / error state (`ui-guidelines.md`).
- Style: theo `docs/03-san-pham/ui-guidelines.md`, tái sử dụng `components/ui/`.

## Chung
- Commit theo Conventional Commits (xem `.agents/rules/git.md`).
- Không hardcode dữ liệu nghiệp vụ (giá, %, số ngày, số lượt) — xem `.agents/rules/general.md`.
- Thuật ngữ thống nhất với docs: `reservedQuantity` (không phải `reserveQuantity`/`heldQuantity`),
  `voucher` (không phải `promotionCode`), `combo` (không phải `bundle`), `isFromStaff`.
