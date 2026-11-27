# Plan: Yêu thích (FR-20) — module `favorite`

- **Ngày tạo:** 2026-11-27
- **Người/agent phụ trách:** Người 4 (Claude Code), nhánh `feature/notification` (Q1 của `notification.md`: làm trước Phần 7)
- **Tier:** 2
- **Module backend liên quan:** `favorite` (mới); đọc qua Service của `catalog` (`ProductService`), `combo` (`ComboService`),
  `article` (thêm `ArticleQueryService`)
- **Module/feature frontend liên quan:** `shop/ShopProvider`, `product/ProductInfo`, `combo/ComboPage`, `cart/CartPage`,
  `account/AccountPage`; `services/favoriteService.ts`, `types/api/favorite.ts`

## Mục tiêu
Khách hàng (role CUSTOMER) thêm / bỏ yêu thích sản phẩm, combo, bài viết và xem danh sách theo loại. Dữ liệu ở server (thay
danh sách slug đang lưu localStorage), làm nguồn người nhận cho `FAVORITE_ON_SALE` / `FAVORITE_BACK_IN_STOCK` (notification
Phần 7).

## Quyết định (ghi D-47)
- **Bỏ `isFavorite` khỏi `ProductDetail` / `ComboDetail` / `ArticleDetail`.** Trang chi tiết render ở server (`serverGet`, không
  gửi token) nên trường này không bao giờ có giá trị. Thay bằng `GET /users/me/favorites/ids` → `{productIds, comboIds,
  articleIds}`: client gọi một lần khi khách đăng nhập rồi tô tim ở mọi chỗ (trang chi tiết, giỏ hàng).
- **Thêm:** mặt hàng phải đang hiển thị (sản phẩm / combo `is_active`, bài viết `is_published`), không thì `NOT_FOUND`. Đã có →
  200 + id cũ (idempotent). Ghi bằng `INSERT … ON CONFLICT DO NOTHING` trên unique index một phần → hai yêu cầu song song không lỗi,
  cùng một id.
- **Bỏ theo mặt hàng** (`DELETE /favorites?productId=…`) idempotent: chưa thích vẫn 200. **Bỏ theo id**: không phải của mình →
  `NOT_FOUND`. Bỏ được cả khi mặt hàng đang ẩn.
- **Danh sách:** `type` bỏ trống = cả 3 loại; mới thích trước; chỉ trả mặt hàng đang hiển thị (ẩn thì không mất dòng — bật lại là
  hiện lại). `page` ≥ 1, `limit` 1–100 (mặc định 12). Combo trả `ComboSummary` (chứa đủ `ItemSummary`).
- Không giới hạn số mục yêu thích; không thêm mã lỗi mới.

## Phạm vi (trong/ngoài)
- **Trong:** `V18__favorites.sql` (bảng 3.18 + index); entity, repository, `FavoriteService`, `FavoriteController` (4 endpoint có
  sẵn trong api.md + `/ids`); `FavoriteService.customersFavoriting(...)` cho notification Phần 7; frontend: service, `ShopProvider`
  (tập id từ API thay localStorage), nút tim ở trang sản phẩm + trang combo, "Yêu thích" ở giỏ hàng, tab tài khoản "Danh sách yêu
  thích" chia Sản phẩm / Combo.
- **Ngoài:** nút tim trên thẻ sản phẩm ở trang danh sách; tim + tab "Bài viết" ở frontend (trang blog còn đọc mock JSON, chưa nối
  API — backend đã hỗ trợ `articleId`); chuyển danh sách slug localStorage cũ lên server (bỏ đi — dữ liệu mock).

## File sẽ tạo/sửa
| File | Tạo mới / Sửa | Ghi chú |
|---|---|---|
| `backend/src/main/resources/db/migration/V18__favorites.sql` | Tạo | kiểm lại số V trên `origin` |
| `backend/.../favorite/{Favorite,FavoriteRepository,FavoriteService,FavoriteController}.java`, `favorite/dto/*` | Tạo | |
| `backend/.../catalog/ProductService.java` | Sửa | `activeSummaries(ids)`, `isActive(id)` (Người 2 — báo) |
| `backend/.../combo/ComboService.java` | Sửa | `activeSummaries(ids)`, `isActive(id)` (Người 2 — báo) |
| `backend/.../auth/SecurityConfig.java` | Sửa | thêm `/api/users/me/favorites/**` vào nhóm CUSTOMER (Người 3 — báo) |
| `frontend/src/lib/catalogCards.ts`, `services/catalogService.ts` | Tạo / Sửa | chuyển `toCard`, `comboCard` ra file dùng được ở client |
| `frontend/src/components/features/favorite/{FavoriteButton,FavoriteList}.tsx`, `styles/shop/custom.css` | Tạo / Sửa | |
| `backend/.../article/ArticleQueryService.java` | Tạo | `publishedSummaries(ids)`, `isPublished(id)` (Người 2 — báo) |
| `backend/src/test/.../favorite/*` | Tạo | schema + API |
| `frontend/src/types/api/favorite.ts`, `services/favoriteService.ts` | Tạo | |
| `frontend/src/components/features/shop/ShopProvider.tsx` | Sửa | `favorites`, `isFavorite`, `toggleFavorite`; bỏ `wishlist` |
| `frontend/src/components/features/{product/ProductInfo,combo/ComboPage,cart/CartPage,account/AccountPage}.tsx` | Sửa | |
| `frontend/src/types/api/catalog.ts` | Sửa | bỏ `isFavorite` |
| `docs/02-ky-thuat/{database,api}.md`, `docs/03-san-pham/{features,pages,use-cases}.md`, `docs/99-ghi-chu/decisions.md` | Sửa | trước khi code |
| `.agents/plans/active/backend-roadmap.md`, `.agents/progress.md` | Sửa | tick N4-02, F-09 (phần favorites) |

## Thứ tự thực hiện
1. Docs (D-47, database 3.18, api 4.2/4.3/4.6, features FR-20, pages, use-cases).
2. V18 + entity + `FavoriteSchemaTest`.
3. Service + controller + hàm đọc ở catalog/combo/article + `FavoriteApiIntegrationTest`.
4. Frontend.
5. Notification Phần 7 (`notification.md` NT-24..27).

## Ảnh hưởng tới hệ thống hiện có
- Không đổi API đang chạy (chỉ bỏ field `isFavorite?` chưa bao giờ được trả). `ProductService`, `ComboService` chỉ thêm hàm đọc.
- `ShopProvider.wishlist` (localStorage) bị bỏ: danh sách yêu thích mock cũ của trình duyệt mất (chấp nhận — chưa phải dữ liệu thật).
- Xoá sản phẩm / combo / bài viết / user → `ON DELETE CASCADE` xoá dòng yêu thích.

## Cách test
- **Schema:** trỏ 0 hoặc 2 đối tượng bị chặn; thích trùng bị chặn (từng loại); xoá sản phẩm → cascade.
- **API (Testcontainers):** thêm 201 rồi thêm lại 200 cùng id; hai yêu cầu song song → một dòng; mặt hàng ẩn / bài nháp / không tồn
  tại → 404; gửi 0 hoặc 2 id → 400; danh sách lọc `type`, mới trước, bỏ mặt hàng ẩn, đúng `ItemSummary`/`ComboSummary`/
  `ArticleSummary`; `/ids`; xoá của người khác → 404; bỏ theo mặt hàng idempotent; nhân viên / chưa đăng nhập → 403 / 401.
- **Frontend (thủ công):** tim ở trang sản phẩm / combo đổi trạng thái và giữ sau tải lại; chưa đăng nhập → /login; tài khoản
  nhân viên → thông báo; tab yêu thích hiện đúng 2 loại, bỏ thích ở đó; trạng thái loading / empty / error.

## Trạng thái
- [x] Đang làm
- [x] Đã test (backend IT; frontend chưa thử tay)
- [x] Đã cập nhật docs/
- [ ] Đã mở PR — việc của người dùng

## Danh sách task
| ID | Loại | Task | Xong khi |
|---|---|---|---|
| - [x] FV-01 | Docs | D-47, database 3.18 (+ index, V18), api 4.2/4.3/4.6 (bỏ `isFavorite`, thêm `/ids`, hành vi), features FR-20, pages, use-cases | |
| - [x] FV-02 | BE | Kiểm số V trên `origin` (2026-11-27: cao nhất V16, nhánh này có V17) → `V18__favorites.sql`; `Favorite`, `FavoriteType`, `FavoriteRepository` (`findVisible` SQL thuần lọc mặt hàng đang hiển thị) | `validate` sạch |
| - [x] FV-03 | BE | `FavoriteService` (thêm bằng `ON CONFLICT DO NOTHING`, bỏ theo id / theo mặt hàng, danh sách, `ids`, `customersFavoriting`), `FavoriteController`; `ProductService/ComboService.activeSummaries + isActive`, `ArticleQueryService` (mới). `SecurityConfig` thêm `/api/users/me/favorites/**` (trước đó `/ids` lọt sang "mọi role") | |
| - [x] FV-04 | Test | Gộp schema + API thành `FavoriteIntegrationTest` 7 test (CHECK / unique / cascade; idempotent + 404 mặt hàng ẩn / bài nháp + 400; 4 luồng song song → 1 dòng cùng id; danh sách lọc loại, mới trước, phân trang, ẩn → biến mất, bật lại → hiện, `/ids`; xoá của người khác 404, bỏ theo mặt hàng idempotent; nhân viên 403 / chưa đăng nhập 401; người nhận bỏ tài khoản bị khoá). Đột biến (bỏ kiểm hiển thị khi thêm, bỏ `u.is_active`) → 2 test đỏ | IT xanh |
| - [x] FV-05 | FE | `types/api/favorite.ts`, `services/favoriteService.ts`; `ShopProvider` (`isFavorite`, `toggleFavorite` đổi ngay + trả lại khi lỗi, `addFavorite`, `forgetFavorite`; bỏ `wishlist` localStorage); `favorite/FavoriteButton` ở trang sản phẩm + combo; giỏ hàng "Yêu thích" / "Đã yêu thích" cho cả combo; `favorite/FavoriteList` (tab Sản phẩm / Combo, "Xem thêm", bỏ thích, loading / empty / error); `toCard` / `comboCard` chuyển sang `lib/catalogCards.ts` (dùng được ở client, `catalogService` re-export) | `tsc` + eslint sạch; thử tay: chưa |
