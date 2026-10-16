# Plan: Migration đầy đủ 22 bảng + khung entity (thay V1/V2 placeholder)

- **Tier:** 1 (nền cho mọi module)
- **Module backend liên quan:** tất cả (entity), `common`
- **Module/feature frontend liên quan:** không

## Mục tiêu
Tạo schema PostgreSQL đúng đặc tả `docs/02-ky-thuat/database.md` (22 bảng, ràng buộc, index) để các
module bắt đầu code trên cùng một nền. Đồng bộ entity hiện có với docs mới.

## Phạm vi (trong/ngoài)
- Trong phạm vi:
  - Thay `V1__init_tier1.sql` + `V2__tier2.sql` (placeholder, chưa chạy ở đâu — decisions D-14) bằng
    `V1__init_schema.sql` chứa đủ 22 bảng; xoá `V2__tier2.sql`.
  - `V2__seed_dev.sql` chỉ chạy profile dev (dữ liệu mẫu theo `database.md` mục 7) — cấu hình
    `spring.flyway.locations` theo profile.
  - Sửa entity hiện có cho khớp schema: `Product`, `Category`, `Cart`, `CartItem` (bỏ `priceAtAdd`,
    thêm `combo`), `Order`, `OrderItem`, `OrderStatus` (thêm `RETURNED` + `canTransitionTo`).
  - `common/exception/ErrorCode` enum (mã lỗi `api.md` mục 2) + chuẩn hoá `BusinessException`,
    `GlobalExceptionHandler`.
  - `application.yml`: `ddl-auto=validate`, `open-in-view=false`, khối `app.*` (`tech-stack.md`).
- Ngoài phạm vi: service/controller nghiệp vụ (làm theo từng module).

## File sẽ tạo/sửa
| File | Tạo mới / Sửa | Ghi chú |
|---|---|---|
| `backend/src/main/resources/db/migration/V1__init_schema.sql` | Tạo (thay V1 cũ) | 22 bảng theo thứ tự phụ thuộc FK |
| `backend/src/main/resources/db/migration/V1__init_tier1.sql`, `V2__tier2.sql` | Xoá | placeholder |
| `backend/src/main/resources/db/seed/V2__seed_dev.sql` | Tạo | chỉ profile dev |
| `backend/src/main/java/vn/campvivo/**` entity | Sửa/Tạo | khớp `database.md` mục 3 |
| `backend/src/main/java/vn/campvivo/common/exception/*` | Sửa/Tạo | `ErrorCode` |
| `backend/src/main/resources/application.yml` | Sửa | cấu hình `app.*` |

## Thứ tự thực hiện
1. Viết `V1__init_schema.sql`: users → categories → products → product_combos → images, combo_items →
   carts, cart_items → vouchers → orders → order_items, order_status_history, payments, return_requests
   → discount_campaigns, discount_campaign_products → articles → reviews, favorites, support_messages,
   notifications, inventory_logs. Sau đó index (`database.md` mục 3.23).
2. Chạy trên DB sạch (docker compose) — app khởi động với `ddl-auto=validate`.
3. Sửa/tạo entity từng module; chạy lại validate.
4. `ErrorCode` + exception handler.
5. Seed dev.
6. Test: context load + Testcontainers chạy migration.

## Ảnh hưởng tới hệ thống hiện có
Code khung cart/order/catalog hiện tại sẽ đổi (KI-04). Chưa có dữ liệu thật nào bị ảnh hưởng.

## Cách test
- `./scripts/agent-test.sh` (Testcontainers chạy Flyway từ đầu).
- Thử chèn dữ liệu vi phạm CHECK (num_nonnulls, tồn kho âm, reserved > stock) → bị từ chối.

## Trạng thái
- [ ] Đang làm
- [ ] Đã test
- [ ] Đã cập nhật docs/
- [ ] Đã mở PR
