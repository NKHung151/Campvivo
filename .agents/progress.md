# Progress Log (append-only)

Ghi lại NGẮN GỌN sau mỗi phiên làm việc đáng kể (xong 1 feature, 1 bugfix lớn, 1 quyết định
kỹ thuật). Agent phiên sau đọc file này TRƯỚC `project-context` để biết trạng thái gần nhất,
tránh làm lại hoặc đi lệch hướng đã có.

KHÔNG sửa/xoá dòng cũ — chỉ thêm dòng mới lên đầu danh sách bên dưới. Lịch sử đầy đủ hơn nằm
ở Git log; file này chỉ là tóm tắt nhanh cho agent.

## Định dạng mỗi mục
```
### <ngày> — <người/agent> — <module>
- Đã làm:
- Còn dang dở / TODO tiếp theo:
- Plan liên quan (nếu có): .agents/plans/active/<ten>.md
```

---

### 2026-11-05 — Người 3 (Claude Code) — support: chat hỗ trợ khách ↔ nhân viên (N4-06, FR-26) + giao diện
- Đã làm (nhánh `feature/support-n4-06-chat`, một PR gồm backend + giao diện; người dùng nhận làm N4-06 thay Người 4): backend `vn.campvivo.support` —
  `V7__support_messages.sql` (CHỈ bảng `support_messages` + chỉ mục; `notifications` thuộc N4-03 nên KHÔNG tạo; `V5` đã bị đơn hàng dùng nên đổi sang `V7`),
  `SupportController` (`/support/messages`, CUSTOMER) và `AdminSupportController` (`/admin/support/conversations*`, EMPLOYEE + ADMIN, có `@PreAuthorize`),
  `SupportService`, `SupportRealtimeListener` (`@TransactionalEventListener(AFTER_COMMIT)` → `RealtimeSender.toStaff` / `toUser`). Phân trang tin dùng mốc `before=<id>`
  (không dùng `created_at`); hội thoại sắp theo id tin cuối. Giao diện: tab "Hỗ trợ" trong `/account` (+ route `/account/support`), `/admin/support` (hộp thư 2 cột),
  hook `useSupportSocket` (STOMP/SockJS, JWT trong CONNECT, tự nối lại, đồng bộ lại bằng REST khi nối lại), `ChatPanel` dùng chung, `lib/support.ts`.
  Thêm thư viện `@stomp/stompjs` 7.0.0, `sockjs-client` 1.6.1, `@types/sockjs-client` 1.5.4 (đổi `package.json` + lockfile).
- Kiểm chứng: `SupportChatIntegrationTest` 20 test (PostgreSQL thật + STOMP thật: cô lập giữa khách, quyền 401/403/404, phân trang `before`, kiểm tra 1–2000 ký tự, thứ tự hội thoại,
  đẩy đúng người, KHÔNG đẩy khi giao dịch bị huỷ); 8 đột biến đều bị bắt đúng test (bỏ lọc user_id ở `before`, mất cờ `isFromStaff`, bỏ kiểm tra đích là khách, đẩy trước commit,
  đẩy nhầm người/nhầm kênh, đảo thứ tự, bỏ trim); đột biến bỏ `@PreAuthorize` là tương đương (SecurityConfig đã chặn khách ở `/api/admin/**`) nên giữ làm lớp phòng thủ thứ hai.
  Toàn bộ backend 348 test xanh cả thứ tự mặc định lẫn đảo (`reversealphabetical`). Chạy thật (backend 8081): 14 kiểm tra bằng chính `@stomp/stompjs` + `sockjs-client` đã cài
  (tin khách → nhân viên/admin, trả lời → đúng khách, tiếng Việt nguyên vẹn, HTML giữ dạng text, 401/403, token sai bị từ chối ở CONNECT). Frontend: tsc, `next lint`, `next build` sạch;
  hàm thuần (`mergeMessages`, `normalizeContent`, `wsUrlFromApiUrl`) kiểm bằng script `tsx` ngoài repo.
- Lỗi tìm ra khi người dùng thử trên trình duyệt: chat báo "Mất kết nối trực tiếp" (REST vẫn chạy). Nguyên nhân (tái hiện bằng Chrome thật qua puppeteer-core): SockJS gọi `/ws/info`
  với credentials "include" mà CORS trả `Allow-Credentials` rỗng → Chrome chặn. Lỗi có sẵn từ N3-08, bị bỏ sót vì test dùng client Java/Node (không có CORS). Sửa: cấu hình CORS riêng cho
  `/ws/**` với `allowCredentials=true` trong `SecurityConfig` (origin vẫn là danh sách cụ thể); thêm test trong `RealtimeIntegrationTest` (đột biến hoàn lại lỗi → đúng test đó đỏ).
  Sau sửa, kịch bản Chrome thật 2 người dùng đạt: khách gửi → nhân viên thấy ngay không tải lại, nhân viên trả lời → khách thấy ngay, xuống dòng giữ nguyên, HTML hiện nguyên dạng text,
  bố cục điện thoại đúng, không lỗi console. Bài học ghi ở `lessons-learned.md`.
- Chưa kiểm chứng: gõ tiếng Việt có dấu bằng bộ gõ thật (Telex/VNI) với phím Enter, cuộn giữ vị trí khi tải tin cũ, tự nối lại sau khi tắt rồi bật backend, tablet, CI GitHub.
- Hạn chế đã biết: chưa có notification `SUPPORT_MESSAGE` khi nhân viên trả lời (chờ N4-03/N4-04); không có "chưa đọc" / biết nhân viên nào trả lời (KI-02); nhân viên KHÔNG nhận tin trả lời của đồng nghiệp
  qua WebSocket (chỉ thấy khi tải lại hội thoại); chưa có rate limit gửi tin; kết nối WebSocket không tự đóng khi đăng xuất / hết token (D-15); broker trong bộ nhớ → một instance.
- Còn dang dở / TODO tiếp theo: người dùng thử tay trên trình duyệt; báo Người 4 rằng N4-06 đã xong và `V7` đã dùng (số migration `notifications` của họ phải khác); thêm 5 endpoint chat vào test ma trận phân quyền N3-10;
  thêm notification `SUPPORT_MESSAGE` khi N4-03 có.
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (N4-06 đã tick)

### 2026-10-31 — Người 4 (Claude Code) — promotion: lối vào khuyến mãi cho khách (Phần 5b, PM-30 → PM-33)
- Đã làm (người dùng yêu cầu thêm; vẫn CHƯA add/commit/push): docs (`api.md`: `onSale` ở `GET /products`, `GET /campaigns`;
  `pages.md`: `/promotions`). Backend: `ProductQuery.onSale` (`salePrice < price`), `GET /campaigns` (`CampaignService.listActive`,
  `productCount` = sản phẩm đang bán). Frontend: menu "Dọn Kho 70%" → "Khuyến mãi" (`/promotions`, sửa `mocks/data/menu.json`);
  trang `/promotions` (`getPromotionsListing`: mọi sản phẩm đang giảm, mức giảm lớn nhất; cột trái = chiến dịch đang chạy);
  `ListingView` thêm prop tuỳ chọn `priceFilter`, `pageSize`, `sideTitle`, `hideSaleFilter` — mặc định tắt nên trang danh mục/
  thương hiệu không đổi; Flash sale trang chủ lấy `onSale` (giảm nhiều trước, 15 sản phẩm) + nút "Xem tất cả" → `/promotions`;
  PromoBar xen kẽ slide chiến dịch (tên escape HTML) và voucher (`interleave`, `campaignPromoSlide`).
- Kiểm chứng: `CampaignPricingIntegrationTest` 7/7 (thêm: `onSale` không trùng dòng khi chồng chiến dịch + lọc giá; `/campaigns` chỉ
  chiến dịch đang chạy, đếm sản phẩm đang bán), `ProductControllerTest` 6/6; frontend tsc + lint sạch. Chạy thật (DB tạm, đã xoá):
  menu "Khuyến mãi" → `/promotions` 200, PromoBar xen kẽ đúng, Flash sale 8 sản phẩm thật + nút "Xem tất cả".
- Lưu ý: KHÔNG chạy dev server thứ hai trong `frontend/` khi người dùng đang chạy `npm run dev` — dùng chung `.next` (lỗi EPERM
  `.next/trace`, trang trả 404 sai sau hot-reload). Phân trang `/promotions` chưa kiểm được trên trình duyệt vì lý do này.
- Còn dang dở / TODO tiếp theo: người dùng thử tay (PM-17, PM-27 + trang `/promotions`: lọc giá, sắp xếp, phân trang > 24 sản phẩm);
  bộ đếm "Chỉ còn hh:mm:ss" của khối Flash sale vẫn là đếm tới nửa đêm (dữ liệu mẫu), không theo hạn chiến dịch.
- Plan liên quan: `.agents/plans/active/promotion-voucher-campaign.md`

### 2026-10-31 — Người 4 (Claude Code) — promotion: chiến dịch (Phần 4–5 / điểm dừng 2)
- Đã làm: nhánh `feature/promotion` (vẫn CHƯA add/commit/push). Backend: `Product.salePrice` (`@Formula`, D-30) — nguồn duy nhất
  của giá bán sản phẩm; `PricingService.unitPrice` = salePrice, `activeCampaigns(ids)` (chiến dịch tốt nhất cho trường `campaign`);
  `ItemSummary`/`ProductDetail` (+ `campaign`)/`ProductCombo.getItemsTotal` dùng salePrice; `GET /products` lọc `minPrice`/`maxPrice`
  và `price_asc|desc` theo salePrice; `CampaignService`/`CampaignController` (`GET /campaigns/{slug}`, chỉ đang hiệu lực);
  `promotion/admin/` CRUD chiến dịch + `GET /admin/campaigns/overlaps`. Frontend: `catalogApi.getCampaign`, `getPromo` đọc API
  (bỏ mock promos), trang `/promotions/[slug]` (banner, giờ kết thúc), `ProductInfo` nhãn chiến dịch + đếm ngược có số ngày,
  `adminCampaignService`, `/admin/campaigns` (`CampaignListView`), `/admin/campaigns/new` + `[id]` (`CampaignForm`,
  `CampaignProductPicker` — giá dự kiến, cảnh báo chồng chiến dịch, banner tải lên/dán link). Không tạo `PriceTag` (đã có sẵn
  giá gạch + badge % trong `catalogService`) — ghi trong plan.
- Kiểm chứng: `CampaignPricingIntegrationTest` (5: bảng ca BR-6 + làm tròn khớp `Discounts`, lọc/sắp xếp/chi tiết, đặt hàng dùng giá
  sau giảm + combo không đổi, trang chiến dịch 404 khi chưa/hết hạn, admin CRUD + overlaps + 403) xanh; cả bộ backend 202/203 (lỗi còn
  lại là KI-10 có sẵn). Frontend tsc + lint sạch. Chạy thật trên DB tạm (backend 8099 + frontend 3099, đã xoá DB tạm): giá 925.000 →
  740.000, `/promotions/xa-hang-cuoi-mua` 200 (giờ VN đúng), chiến dịch sắp diễn ra 404, PDP có nhãn chiến dịch, các trang admin 200.
- Còn dang dở / TODO tiếp theo:
  - PM-17, PM-27: người dùng thử tay trên trình duyệt (voucher + chiến dịch, mobile 375px).
  - PM-28 (email chào mừng) chờ người dùng cho phép rebase lên `dev`. PM-29: tick roadmap, chuyển plan sang `completed/` khi xong.
  - Link `/promotions/...` trong dữ liệu mock (home.json, menu.json) trỏ slug không có trong DB → 404 cho đến khi thay bằng chiến dịch thật.
- Plan liên quan: `.agents/plans/active/promotion-voucher-campaign.md`

### 2026-10-31 — Người 4 (Claude Code) — promotion: voucher (Phần 1–3 / điểm dừng 1)
- Đã làm: nhánh `feature/promotion` (tạo từ `origin/feature/order-payment-service`; người dùng chốt: không merge/rebase/đụng nhánh
  khác, CHƯA add/commit/push). Docs trước: api.md (`/vouchers/available`, `/vouchers/public`, `/admin/campaigns/overlaps`, kiểu admin,
  mã `VOUCHER_CODE_ALREADY_EXISTS`), database.md (`is_public`, index, seed V7), D-29 (mã công khai + danh sách, không ví cá nhân),
  D-30 (`Product.salePrice` @Formula — chưa code), features FR-16, pages. Backend: `V6__promotion.sql` (3 bảng + FK `fk_orders_voucher`),
  `db/seed/V7__seed_promotions.sql`, entity `Voucher`/`DiscountCampaign` (+ `Discounts`, `VoucherRules` thứ tự lỗi BR-5),
  `VoucherService.quote/consume/release/available/publicList`, nối `OrderService` (preview/ước tính = quote, create = consume sau khoá
  products/combos, `Order` có `voucherId`) và `OrderStateService` (trả lượt), `VoucherController`, `promotion/admin/*` CRUD voucher.
  Frontend: `types/api/promotion.ts`, `promotionService`, `adminVoucherService`, `lib/promotion.ts`, `/admin/vouchers`
  (`VoucherListView` + `VoucherFormModal`), `VoucherList` trong popup `/cart`, PromoBar đọc `/vouchers/public` (SSR, cache 60s).
- Kiểm chứng: `PromotionSchemaTest` (9), `VoucherRulesTest` (20), `VoucherFlowIntegrationTest` (10: trừ/trả lượt khi huỷ/IPN lỗi/hết hạn,
  dùng 2 lần, hết lượt, rollback khi PRICE_CHANGED, 20 luồng còn 1 lượt → 1 đơn, 1 khách 2 đơn song song → 1, API khách + admin) xanh.
  Cả bộ backend: 147/148 xanh — lỗi còn lại có sẵn từ trước (KI-10). Lỗi phát hiện khi test đồng thời: voucher đã đọc lúc ước tính phí
  ship nên `FOR UPDATE` trả entity cũ → đã thêm `em.refresh` sau khoá. Frontend: tsc + lint sạch. Chạy thật trên DB tạm `campvivo_verify`
  (backend 8099, frontend 3099): API khách/admin đúng, PromoBar hiện mã thật, `/`, `/cart`, `/admin/vouchers` trả 200.
- Còn dang dở / TODO tiếp theo:
  - PM-17: người dùng thử tay trên trình duyệt (popup voucher, trang admin, mobile 375px). DB `campvivo` của người dùng sẽ chạy V6, V7
    khi khởi động lại backend. DB tạm `campvivo_verify` còn giữ để test Phần 4 — xoá khi xong.
  - Phần 4–5: chiến dịch (PM-18 → PM-27). PM-28 (email chào mừng) chờ người dùng cho phép rebase lên `dev`.
- Plan liên quan: `.agents/plans/active/promotion-voucher-campaign.md`

### 2026-10-29 — Người 3 — frontend (giao diện đăng ký + xác thực email, đi kèm N3-02)
- Đã làm: nhánh `feature/auth-n3-02-register-ui` (tách từ `dev`). Trang `/register` (`RegisterForm`) và `/verify-email?email=` (`VerifyEmailForm`:
  ô mã 6 số, nút "Gửi lại mã" đếm ngược 60 giây bắt đầu khi vào trang). `authService.register/verifyEmail/resendCode` (đều `skipAuth`);
  `AuthProvider.verifyEmail` dùng chung đường lưu token với `login` (đúng mã → đăng nhập luôn). `LoginForm`: liên kết "Tạo tài khoản"; gặp
  `EMAIL_NOT_VERIFIED` → gọi `resend-code` (bỏ qua `OTP_RESEND_TOO_SOON`) rồi chuyển `/verify-email`. Hook dùng chung `useLandingRedirect`;
  `lib/auth/validation.ts` (luật khớp `RegisterRequest`, đếm byte UTF-8 ≤ 72); `authHref()` giữ `?redirect=` (đã qua `safeRedirectPath`).
  `components/ui/Input`: prop `revealable` (nút Hiện/Ẩn mật khẩu). Docs: `pages.md`.
  Kiểm chứng: `npm run lint`, `tsc --noEmit` xanh; script ngoài repo kiểm tra các hàm thuần (mật khẩu, tên, email, OTP, href/redirect chống open
  redirect); chạy thật backend 8081 + Mailpit: đăng ký → đọc mã trong thư → sai mã `INVALID_OTP` → đúng mã nhận token → email trùng 409 → lỗi theo
  trường; đăng nhập tài khoản chưa xác thực trả `EMAIL_NOT_VERIFIED`, gửi lại ngay thì `OTP_RESEND_TOO_SOON`; token rác trên `verify-email` bị 401
  (chứng minh cần `skipAuth`).
- Còn dang dở / TODO tiếp theo:
  - CHƯA kiểm chứng giao diện trên trình duyệt thật (agent không có trình duyệt); cần người thử `/register` → `/verify-email` ở mobile/desktop.
  - `npm run build` trên `dev` (cả khi chưa có thay đổi này) lỗi prerender `NETWORK_ERROR` ở `/`, `/brands`, `/products`... dù backend đang chạy:
    nghi `serverApi.request` bắt nhầm lỗi `DynamicServerError` của Next (fetch `cache: "no-store"` lúc build) thành `networkError()`. Thuộc phần catalog
    (Người 2), cần báo/chốt người sửa. `tsc` và bước biên dịch của build vẫn xanh.
  - `/forgot-password` và liên kết "Quên mật khẩu?" thuộc N3-04. `EMAIL_ALREADY_EXISTS` hiện chỉ gợi ý "Đăng nhập" (chưa có trang quên mật khẩu).
  - Email nằm trên URL `/verify-email?email=` theo đúng docs.
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (N3-02)

### 2026-11-04 — Người 3 — backend + frontend (N3-07: chi tiết, sửa người dùng của Admin; giao diện `/admin/users`)
- Đã làm (CÙNG nhánh `feature/user-n3-06-admin-user-list`, theo lựa chọn của người phát triển để thử được trên trình duyệt): `GET /api/admin/users/{id}` (thêm `address`, `orderCount` = mọi đơn, `completedOrderCount`, `totalSpent` = tổng đơn COMPLETED;
  đơn RETURNED / CANCELLED không tính) và `PATCH /api/admin/users/{id}` (`AdminUpdateUserRequest`: sửa TỪNG PHẦN, vắng hoặc null = giữ nguyên, `phone` rỗng = xoá; `role` / `isActive` đổi giá trị THẬT thì `UserRepository.changeAccess` tăng `token_version`
  trong cùng một câu UPDATE nguyên tử nên token người đó bị từ chối ngay; gửi lại giá trị cũ hoặc chỉ sửa tên / SĐT thì không tăng; tự khoá mình hoặc tự đổi role của mình → `CANNOT_MODIFY_SELF` và từ chối trọn gói; vẫn tự sửa được tên / SĐT). Chỉ ADMIN.
  Thêm vào module của Người 1 (CHỈ THÊM MỚI): `OrderQueryService.statsForUser` + `UserOrderStats` + 3 hàm trong `OrderRepository` (`countByUserId`, `countByUserIdAndStatus`, `sumFinalAmountByUserIdAndStatus`) — cần báo Người 1.
  Test `AdminUserDetailIntegrationTest` (14, PostgreSQL thật; đơn chèn bằng SQL tối thiểu). Kiểm tra "đột biến" 7 cơ chế (không tăng `token_version`, bỏ chặn tự sửa, tăng cả khi không đổi, bỏ chặn ADMIN, chi tiêu tính mọi trạng thái, SĐT rỗng không xoá, đếm đơn người khác) → đúng các test tương ứng đỏ;
  M3 / M5 / M7 phải chạy RIÊNG vì khi gộp với M1 bị che khuất.
  Giao diện `/admin/users` (thay trang "đang xây dựng"): `UserListView` (Ant Design Table, tìm, lọc vai trò / trạng thái, phân trang, bộ lọc và `?user=` trên URL) và `UserDrawer` (chi tiết + form sửa, hộp xác nhận khi đổi vai trò / khoá, vô hiệu hoá vai trò và công tắc ở dòng của chính mình),
  `adminUserService.ts`, `types/api/adminUser.ts`, `userLabels.ts`. Người phát triển đã tự thử trên trình duyệt (danh sách, tìm, lọc, chi tiết, sửa, đổi vai trò, khoá / mở khoá, tự sửa mình, nhân viên bị chặn) và xác nhận ổn.
  Lỗi giao diện do người phát triển phát hiện khi thử: ngăn kéo hiện họ tên / SĐT của người mở ĐẦU TIÊN khi mở người khác — Ant Design Form giữ giá trị của đối tượng `form` (nằm ở thành phần cha) và để nó ghi đè `initialValues` khi dựng lại (`rc-field-form` `setInitialValues`: `merge(initialValues, store)`);
  đã sửa bằng `useEffect` đồng bộ `setFieldsValue(toForm(detail))` + `preserve={false}`.
  Docs: `api.md` (kiểu `AdminUserDetail` + hành vi), `local-development.md`, `pages.md`, `troubleshooting.md` (bẫy mã hoá dòng lệnh với `curl` trong Git Bash).
- Còn dang dở / TODO tiếp theo:
  - Chưa chặn trường hợp hai admin đồng thời hạ quyền / khoá lẫn nhau khiến hệ thống không còn admin (đã ghi trong `api.md`). Kết nối WebSocket đang mở của người bị khoá không tự đóng.
  - Nhân viên (EMPLOYEE/ADMIN) vẫn chưa có giao diện sửa hồ sơ / đổi mật khẩu của chính mình (`/account` chỉ dành cho khách); backend đã cho phép từ N3-05.
  - Chưa thử giao diện admin users trên tablet; dự án chưa có công cụ test giao diện (lỗi ngăn kéo ở trên lẽ ra bị test giao diện bắt được).
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (N3-07)

### 2026-11-04 — Người 3 — backend (N3-06: danh sách người dùng của Admin)
- Đã làm: nhánh `feature/user-n3-06-admin-user-list` (tách từ `dev`). `GET /api/admin/users` (`user/admin/`: `AdminUserController` `@PreAuthorize("hasRole('ADMIN')")` — `SecurityConfig` cho `/api/admin/**` qua cả EMPLOYEE nên phải chặn ở endpoint;
  `AdminUserService`, `AdminUserQuery`, `AdminUserResponses.Summary`) và `UserSpecifications` + `UserRepository extends JpaSpecificationExecutor` theo mẫu `ProductSpecifications` của Người 2. Tham số: `q` (email HOẶC họ tên chứa từ khoá,
  không phân biệt hoa thường kể cả chữ hoa có dấu; `trim`; rỗng = không lọc; ≤ 100 ký tự; `%`, `_`, `\` là chữ thường — thoát bằng `ESCAPE`, khác `UserService.findIdsByKeyword` vốn xoá các ký tự này), `role`, `isActive`, `page` ≥ 1,
  `limit` 1–100 (mặc định 12). Mới nhất trước (`createdAt` rồi `id` giảm dần). Mỗi dòng: `id, email, fullName, phone, role, isActive, emailVerified, createdAt` (+07:00); KHÔNG có `address`, mật khẩu, OTP, `token_version`.
  Giữ nguyên `UserService.findIdsByKeyword` vì `AdminOrderService` của Người 1 đang dùng. Test: `AdminUserIntegrationTest` (11, PostgreSQL thật; cô lập bằng nhãn riêng mỗi test). Kiểm tra "đột biến": bỏ chặn ADMIN, đảo sắp xếp, bỏ lọc role và
  `isActive`, bỏ `trim`, bỏ thoát ký tự đại diện → đúng các test tương ứng đỏ; bỏ riêng nhánh tìm theo họ tên / theo email → đúng các test tìm kiếm đỏ. (Bỏ `lower()` trên cột email KHÔNG làm test nào đỏ vì email luôn lưu chữ thường — đột biến tương đương.)
  Docs: `api.md` (kiểu `AdminUserSummary` + hành vi), `local-development.md`.
- Còn dang dở / TODO tiếp theo:
  - N3-07 (`GET/PATCH /admin/users/{id}`: số đơn, tổng chi tiêu qua `OrderQueryService` — đã có trên `dev`; đổi role / khoá phải tăng `token_version`, chặn `CANNOT_MODIFY_SELF`) và giao diện `/admin/users` (danh sách + chi tiết + sửa, làm cùng N3-07). Trang hiện là "đang xây dựng".
  - Chưa tìm không dấu ("nguyen" không ra "Nguyễn"): cần `unaccent` của PostgreSQL (migration `CREATE EXTENSION`); `pg_trgm` nếu bảng lớn và chậm (`database.md` dòng 384). Không dùng Elasticsearch (quá nặng, phải đồng bộ chỉ mục).
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (N3-06)

### 2026-11-05 — Người 3 — backend (N3-05: sửa hồ sơ, đổi mật khẩu)
- Đã làm: nhánh `feature/user-n3-05-profile` (tách từ `dev`). `PUT /users/me` (`UpdateProfileRequest`: CHỈ `fullName` 2–100 sau `trim`, `phone` tuỳ chọn `^0\d{9}$` — cùng luật với SĐT khi đặt hàng,
  `address` ≤ 500; phone/address rỗng = xoá; email, role và mọi trường khác trong body bị bỏ qua, mọi role đã đăng nhập dùng được; không làm token hết hiệu lực) và
  `PUT /users/me/password` (`ChangePasswordRequest`; mật khẩu mới theo `PasswordPolicy` và được kiểm TRƯỚC khi so mật khẩu cũ; sai mật khẩu hiện tại → `INVALID_CURRENT_PASSWORD`;
  được phép trùng mật khẩu cũ). `UserRepository.changePassword`: một câu UPDATE nguyên tử đổi hash + tăng `token_version`, chỉ ghi khi hash trong DB vẫn là bản vừa xác nhận
  (compare-and-set) và tài khoản còn hoạt động → lần đổi / đặt lại khác chen vào thì `INVALID_CURRENT_PASSWORD`, không ghi đè. Thành công thì MỌI token cũ (kể cả của thiết bị đang đổi)
  hết hiệu lực, không trả token mới. `UserService` nhận thêm `PasswordEncoder`. Test: `ProfileIntegrationTest` (17, PostgreSQL thật: sửa đúng, trim, rỗng thành null, `fieldErrors`,
  email/role/isActive/tokenVersion trong body bị bỏ qua, không đụng user khác, 401 khi chưa đăng nhập, nhân viên cũng sửa được, đổi mật khẩu đúng/sai/yếu/quá 72 byte, token cũ 401,
  mật khẩu mới đăng nhập được, đổi song song chỉ một yêu cầu thắng, và một test xác định cho compare-and-set). Kiểm tra "đột biến": bỏ tăng `token_version`, bỏ kiểm mật khẩu cũ, bỏ kiểm 72 byte,
  không đổi rỗng thành null → đúng 6 test đỏ; bỏ riêng compare-and-set → đúng 2 test đỏ (test xác định và test song song). Docs: `api.md` (hành vi đã hiện thực), `local-development.md`.
- Giao diện (CÙNG PR, theo lựa chọn của người phát triển để thử được trên trình duyệt): tab "Hồ sơ" (`ProfileForm`: email chỉ đọc, họ tên, SĐT tuỳ chọn, địa chỉ mặc định tuỳ chọn; lưu xong
  `AuthProvider.setProfile` cập nhật tên/SĐT ở menu và form giao hàng) và tab "Đổi mật khẩu" (`ChangePasswordForm`: lỗi mật khẩu cũ hiện ngay dưới ô đó; thành công thì `clearSession()` — không gọi API vì token đã
  vô hiệu — rồi về `/login?changed=1` kèm thông báo). `userService`, `types/api/user.ts`, `validatePhone`/`validateAddress` (cùng luật backend), `LoginForm` nhận `notice: "reset" | "changed"`.
  Kiểm chứng: `lint`, `tsc` xanh; `npm run build` KHÔNG backend (như CI) exit 0; script hàm thuần ngoài repo; chạy thật backend 8081 + Next 3100, người phát triển tự thử các kịch bản trên trình duyệt và xác nhận ổn.
  Phát hiện khi thử bằng PowerShell 5.1: gửi tiếng Việt không đúng UTF-8 (`Nguy?n Van An`) và không đọc được thân lỗi 4xx → đã ghi vào `troubleshooting.md` và sửa hướng dẫn trong `local-development.md`.
- Còn dang dở / TODO tiếp theo:
  - Nhân viên (EMPLOYEE/ADMIN) chưa có giao diện sửa hồ sơ / đổi mật khẩu của mình (`/account` chỉ dành cho khách); backend đã cho phép. Hoãn tới phần giao diện admin user (N3-07).
  - Chưa thử giao diện trên tablet.
  - Chưa có giới hạn số lần thử mật khẩu hiện tại: ai có token đánh cắp có thể đoán mật khẩu không giới hạn (nợ rate limit chung của auth).
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (N3-05)

### 2026-11-03 — Người 3 — backend (N3-08: WebSocket STOMP + RealtimeSender)
- Đã làm: nhánh `feature/realtime-n3-08-websocket` (tách từ `dev`). `WebSocketConfig` (endpoint `/ws` có SockJS, simple broker `/queue`, tiền tố `/user`, origin lấy từ
  `app.cors.allowed-origins`, khung tối đa 8 KB), `StompAuthChannelInterceptor` (xác thực JWT ở khung CONNECT; SUBSCRIBE chỉ `/user/queue/notifications` và `/user/queue/support`;
  mọi khung SEND bị từ chối — client chỉ nhận), `TokenAuthenticator` (tách từ `JwtAuthenticationFilter`, dùng chung HTTP và WebSocket nên hai nơi không lệch nhau),
  `RealtimeSender` (`toUser`, `toStaff`: chỉ nhân viên EMPLOYEE/ADMIN còn hoạt động VÀ đang kết nối; gửi lỗi chỉ ghi log, kênh sai là `IllegalArgumentException`) và
  `StompRealtimeSender`, `UserRepository.findActiveIdsByRoleIn`. Test: `RealtimeIntegrationTest` (12, client STOMP thật qua SockJS + PostgreSQL thật: token hợp lệ nhận tin,
  không token / token rác / sai chữ ký / thiếu Bearer / `token_version` cũ / tài khoản khoá bị `ERROR`, A không nhận tin của B, `toStaff` tới EMPLOYEE và ADMIN không tới CUSTOMER,
  SUBSCRIBE kênh lạ và client SEND bị chặn, origin) và `StompRealtimeSenderTest` (5, Mockito). Kiểm tra "đột biến": gỡ xác thực CONNECT, bỏ kiểm `token_version`, cho SUBSCRIBE mọi kênh,
  cho client SEND, gộp khách vào `toStaff`, đặt origin cho phép sai → đúng các test tương ứng đỏ. Chạy thật `spring-boot:run` cổng 8081 + kịch bản Node gửi khung STOMP bằng tay:
  token hợp lệ nhận CONNECTED, các ca vi phạm đều nhận ERROR rồi bị đóng. Docs: `api.md` mục 4.9, `architecture.md` (mục Realtime), `local-development.md`, `lessons-learned.md`.
- Còn dang dở / TODO tiếp theo:
  - Người 4 (N4-04 thông báo, N4-06 chat) gọi `RealtimeSender` qua `RealtimeDestinations.NOTIFICATIONS` / `SUPPORT`; chưa có module nào gọi nên chưa thấy tin thật trên giao diện.
  - Kết nối đang mở KHÔNG tự đóng khi token hết hạn (24 giờ), đăng xuất hay tài khoản bị khoá — chỉ kiểm lúc CONNECT (đã ghi trong `api.md`); muốn chặt hơn phải ngắt chủ động khi `token_version` đổi.
  - Broker trong bộ nhớ: chỉ đúng khi chạy MỘT instance backend.
  - Thư viện STOMP phía trình duyệt (`@stomp/stompjs`, `sockjs-client`) và trang chat `/account/support`, `/admin/support` chưa làm; cần chốt ai làm giao diện chat (AGENTS.md §9 và roadmap chưa khớp).
  - Chưa thử trên trình duyệt thật.
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (N3-08)

### 2026-11-04 — Người 3 — frontend (giao diện quên / đặt lại mật khẩu, đi kèm N3-04)
- Đã làm: nhánh `feature/auth-n3-04-reset-password-ui` (tách từ `dev`). Trang `/forgot-password` (`ForgotPasswordForm`: nhập email → luôn sang bước sau khi server
  trả 200, không báo email có tồn tại hay không) và `/reset-password?email=` (`ResetPasswordForm`: mã 6 số + mật khẩu mới có nút Hiện/Ẩn, "Gửi lại mã" đếm ngược 60 giây
  qua `resend-code` purpose `RESET_PASSWORD`; thành công về `/login?reset=1` kèm thông báo xanh, KHÔNG đăng nhập luôn). `authService.forgotPassword/resetPassword` (đều
  `skipAuth`). Hook `useResendCountdown` tách ra dùng chung với `VerifyEmailForm`. `LoginForm`: liên kết "Quên mật khẩu?" + thông báo `?reset=1`; `RegisterForm`: gợi ý "Quên mật khẩu?"
  khi email đã tồn tại. `authHref` giữ `?redirect=` xuyên suốt. Docs: `pages.md`.
  Kiểm chứng: `npm run lint`, `tsc --noEmit` xanh; `npm run build` KHÔNG backend (như CI) exit 0; script ngoài repo kiểm tra `authHref`/luật mật khẩu/OTP; chạy thật backend 8081 + Mailpit:
  email lạ → 200; email thật → mã trong thư; xin lại ngay vẫn 200 nhưng không có thư thứ hai; mật khẩu yếu → `VALIDATION_ERROR` ở `newPassword`; mã sai → `INVALID_OTP`; mã đúng → 200;
  dùng lại mã → `INVALID_OTP`; đăng nhập mật khẩu mới 200, mật khẩu cũ `INVALID_CREDENTIALS`, token cũ 401; token cũ gửi kèm `forgot-password` bị 401 (lý do cần `skipAuth`).
- Còn dang dở / TODO tiếp theo:
  - Giao diện trên trình duyệt thật cần người thử (agent không có trình duyệt); mobile/tablet chưa xem.
  - Gửi thư tới hộp thư thật (Gmail...) vẫn chưa kiểm chứng (mới thử qua Mailpit).
  - Xin lại mã reset bằng `resend-code` vẫn báo `OTP_RESEND_TOO_SOON` cho email thật (lộ email có tồn tại, có từ N3-02); chưa có rate limit theo IP.
  - Việc kế tiếp của Người 3: N3-05 (hồ sơ, đổi mật khẩu), N3-08 (WebSocket, Người 4 đang chờ).
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (N3-04)

### 2026-11-02 — Người 3 — backend (N3-04: quên và đặt lại mật khẩu)
- Đã làm: nhánh `feature/auth-n3-04-reset-password` (tách từ `dev`). `POST /auth/forgot-password` (luôn 200: email lạ / tài khoản khoá / gửi lại < 60 giây
  đều im lặng không gửi thư; chỉ `EMAIL_SEND_FAILED` vẫn báo; tài khoản chưa xác thực email vẫn được gửi mã) và `POST /auth/reset-password`
  (`PasswordResetService`: mật khẩu > 72 byte bị từ chối TRƯỚC khi đụng tới mã; email lạ và mã sai cùng `INVALID_OTP`; một câu UPDATE nguyên tử
  `UserRepository.resetPassword` đổi hash + tăng `token_version` + đặt `email_verified = true`, chỉ với tài khoản còn hoạt động; không trả token).
  `PasswordPolicy` gom luật mật khẩu dùng chung với đăng ký. Test: `PasswordResetIntegrationTest` (20, PostgreSQL thật + GreenMail + đồng hồ chỉnh tay,
  gồm reset song song cùng mã chỉ một yêu cầu thắng, token cũ → 401, mã `VERIFY_EMAIL` không dùng để reset). Kiểm tra "đột biến": cố ý bỏ tăng
  `token_version`, bỏ `email_verified`, bỏ điều kiện tài khoản hoạt động, ném lỗi gửi-quá-sớm, bỏ kiểm 72 byte → đúng 6 test đỏ, rồi hoàn lại.
  Docs: `api.md` (hành vi đã hiện thực), `local-development.md` (thử bằng PowerShell), `lessons-learned.md`. `mvn clean test` toàn bộ: 269 test xanh.
  CÙNG PR có sửa hạ tầng test (phát hiện khi chạy mốc trên `dev`): `SecurityIntegrationTest` mong `/api/admin/orders` là 404 nhưng Người 1 đã thêm
  controller thật (200) → đổi sang đường dẫn thăm dò không có controller; mỗi context test giữ ~10 kết nối nên cả bộ test cạn `max_connections` (100) của
  PostgreSQL dùng chung (`too many clients already`) → giới hạn Hikari pool = 5 trong `PostgresTestContainerConfig`.
  CI GitHub báo thêm 41 lỗi: test đơn hàng/khuyến mãi (`OrderPaymentFlowIntegrationTest`, `AdminOrderIntegrationTest`, `CampaignPricingIntegrationTest`,
  `VoucherFlowIntegrationTest`) không dọn giỏ/đơn nên test catalog/combo (`DELETE FROM products`) lỗi `cart_items_product_id_fkey` khi chạy sau chúng
  (Windows chạy catalog trước nên không thấy). Đã tái hiện bằng `-Dsurefire.runOrder=reversealphabetical` và sửa bằng `@AfterEach` `TRUNCATE ... CASCADE`
  trong 4 lớp test đó (chạm test của Người 1 và promotion, chỉ thêm hàm dọn).
- Còn dang dở / TODO tiếp theo:
  - Frontend `/forgot-password`, `/reset-password` + liên kết "Quên mật khẩu?" (PR riêng sau PR này).
  - `dev` (b145ae3) trước PR này có 1 test đỏ (`SecurityIntegrationTest.employeeAndAdminPassAdminPathRule`) nên CI backend của các PR khác có thể đỏ cho
    tới khi PR này merge; ai thêm lớp test tích hợp mới cũng sẽ gặp cạn kết nối nếu thiếu giới hạn pool.
  - Dò email: `resend-code` với purpose `RESET_PASSWORD` vẫn lộ `OTP_RESEND_TOO_SOON`/`EMAIL_SEND_FAILED` cho email thật (có từ N3-02); độ trễ
    `forgot-password` khác nhau giữa email có và không có (gửi mail). Chưa có rate limit theo IP.
  - Mỗi user chỉ một ô OTP: xin mã đặt lại mật khẩu ghi đè mã xác thực email đang chờ (và ngược lại).
  - `CartService`: Người 1 đã hiện thực `createForUser` và xoá `StubCartService` (PR #19) — mục nợ cũ đã đóng.
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (N3-04)

### 2026-10-29 — Người 3 — frontend (sửa `next build` lỗi NETWORK_ERROR trên CI)
- Đã làm: nhánh `fix/frontend-server-api-dynamic-build` (tách từ `dev`). Nguyên nhân (đã chứng minh bằng cách in lỗi gốc): `services/serverApi.ts`
  (F-14) bọc `fetch` no-store trong `catch` đổi MỌI lỗi thành `NETWORK_ERROR`, nuốt `DYNAMIC_SERVER_USAGE` mà Next ném lúc prerender → `next build`
  hỏng ở `/`, `/brands`, `/products` khi không có backend (CI) và cả khi có backend. Sửa: để lỗi điều khiển luồng của Next (`digest` =
  `DYNAMIC_SERVER_USAGE` / `BAILOUT_TO_CLIENT_SIDE_RENDERING`) đi tiếp. Kiểm chứng: `npm run build` không có backend (như CI) exit 0, các trang
  catalog chuyển sang render động; chạy `next start` với backend tắt thì lỗi mạng thật vẫn được ghi nhận là `NETWORK_ERROR`. Trước khi sửa, `dev` sạch
  build exit 1. Docs: `lessons-learned.md`.
- Còn dang dở / TODO tiếp theo:
  - Chạy thật với backend tắt, `/brands` trả HTTP 500 (trang catalog không bắt `ApiError`); roadmap N2-04 yêu cầu "tắt BE → trang báo lỗi thân thiện".
    Việc này thuộc catalog (Người 2), không nằm trong PR sửa này.
  - Không xem được CI từ máy này; kết quả CI sau khi sửa cần người mở PR xác nhận.
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (F-14, N2-04)

### 2026-10-26 — Người 3 — backend (N3-02: đăng ký, xác thực email, gửi lại mã)
- Đã làm: nhánh `feature/auth-n3-02-register` (XẾP CHỒNG lên `feature/auth-n3-01-otp`: PR 1 phải merge trước). `POST /auth/register` (201 `{email}`;
  email chưa xác thực đăng ký lại → ghi đè mật khẩu/họ tên + mã mới; gửi lại < 60 giây → `OTP_RESEND_TOO_SOON`, rollback không ghi đè;
  gửi mail lỗi → `EMAIL_SEND_FAILED`, không tạo user; email đã xác thực → `EMAIL_ALREADY_EXISTS`; mật khẩu > 72 byte → `VALIDATION_ERROR`),
  `POST /auth/verify-email` (trả `{accessToken, user}`; email lạ và mã sai cùng `INVALID_OTP`, có băm giả chống dò qua độ trễ; tài khoản
  khoá → `ACCOUNT_DISABLED`), `POST /auth/resend-code` (200 im lặng với email lạ / tài khoản khoá / đã xác thực). `RegistrationService`,
  DTO, `UserRepository.markEmailVerified`, giao diện `CartService` + `StubCartService` (module cart, TẠM — bảng `carts` chưa có). Test:
  `RegistrationIntegrationTest` (25, PostgreSQL thật + GreenMail + đồng hồ chỉnh tay, gồm đăng ký song song cùng email). Kiểm tra "đột biến":
  cố ý cho đăng ký đè tài khoản đã xác thực và bỏ kiểm tra tài khoản khoá → đúng 2 test đỏ. Chạy thật đầu-cuối trên ứng dụng + Mailpit:
  đăng ký → đọc mã trong thư → xác thực → `/users/me`. Docs: `api.md` (hành vi đã hiện thực), `local-development.md` (cách thử bằng PowerShell).
- Còn dang dở / TODO tiếp theo:
  - Người 1: hiện thực `CartService` thật (tạo dòng `carts`) rồi XOÁ `StubCartService` (có TODO trong code).
  - RỦI RO đã biết, theo đúng FR-01: đăng ký lại email CHƯA xác thực ghi đè mật khẩu — kẻ biết email người khác có thể ghi đè mật khẩu của tài
    khoản đang chờ xác thực; nếu người thật sau đó xác thực bằng mã trong hộp thư của họ thì tài khoản mang mật khẩu của kẻ kia.
    Cân nhắc (cần nhóm quyết định): không ghi đè mật khẩu khi đăng ký lại, hoặc yêu cầu mật khẩu mới chỉ có hiệu lực sau khi xác thực.
  - Chưa có giới hạn theo IP (chỉ chống gửi lại 60 giây theo từng email) → có thể bị lợi dụng spam thư tới email người khác; nên cân nhắc rate limit
    trước khi triển khai thật. Độ trễ phản hồi của `register`/`resend-code` khác nhau giữa email có và không có (gửi mail) → vẫn có thể dò email qua thời gian.
  - Đăng ký song song cùng email: yêu cầu thứ hai nhận `EMAIL_ALREADY_EXISTS` dù người dùng chỉ bấm hai lần.
  - Frontend (PR 3): trang `/register`, `/verify-email` (đếm ngược 60 giây), liên kết "Tạo tài khoản"; gọi 3 endpoint với `{ skipAuth: true }`.
  - N3-04 (quên mật khẩu) dùng lại `OtpService`/`MailService`/`resend-code` (đã hỗ trợ purpose RESET_PASSWORD).
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (N3-02)

### 2026-10-26 — Người 3 — backend (N3-01: OTP + gửi email)
- Đã làm: nhánh `feature/auth-n3-01-otp`. `OtpService` (BR-11: mã 6 số `SecureRandom`, chỉ lưu BCrypt, hạn `app.otp.ttl`, tối đa
  `app.otp.max-attempts` lần thử, gửi lại cách ≥ `app.otp.resend-interval`; `issue(User, purpose)` ghi OTP vào entity, `verify(userId,
  purpose, code)` chạy giao dịch RIÊNG REQUIRES_NEW: lượt đoán không bị mất khi người gọi rollback, tăng lượt bằng UPDATE nguyên tử nên
  đoán song song không vượt giới hạn, OTP dùng một lần), `MailService` (gửi đồng bộ, mẫu Thymeleaf `templates/mail/otp.html`, tiêu đề theo
  mục đích, escape HTML, lỗi → `EMAIL_SEND_FAILED`, không log mã), cấu hình `spring.mail.*` (mặc định Mailpit `localhost:1025`, timeout
  5/10 giây), `management.health.mail.enabled=false`, `app.mail.from/from-name`, thêm **Mailpit** vào `docker-compose.yml` (đọc thư ở
  http://localhost:8025). Test: `OtpServiceTest` (14, PostgreSQL thật: gồm đoán song song, rollback người gọi, hết hạn đúng biên),
  `MailServiceTest` (5, GreenMail). Kiểm tra "đột biến": cố ý làm yếu giới hạn lượt đoán và giao dịch riêng → đúng 4 test đỏ.
  Đã gửi thử thư thật tới Mailpit và xem lại nội dung. Docs: `tech-stack.md`, `docker.md`, `local-development.md`,
  `troubleshooting.md`, `.env.example` (SMTP_* để comment: biến rỗng ghi đè mặc định).
- Còn dang dở / TODO tiếp theo:
  - N3-02 (PR kế tiếp): `register`, `verify-email`, `resend-code`. Người gọi `issue()` phải lưu user trong giao dịch của mình và gửi mail
    SAU `issue()` (lỗi mail → rollback, không tạo user/OTP). `verify()` đừng gọi khi giao dịch của người gọi đang giữ thay đổi chưa commit
    trên chính dòng user đó. Tạo giỏ hàng rỗng khi đăng ký: bảng `carts` chưa có → dùng stub `CartService.createForUser` (F-11), ghi TODO.
  - `otp_last_sent_at` được GIỮ khi OTP dùng xong (vẫn giới hạn tần suất gửi lại); BR-11 chỉ nói "xoá các cột OTP".
  - Mặc định SMTP `localhost:1025` ở môi trường thật sẽ gây `EMAIL_SEND_FAILED` nếu quên đặt `SMTP_*`.
  - Test OTP dùng `MutableTestClock` lồng riêng trong test; khi F-04 (Người 4) có `MutableClock` chung thì gom lại.
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (N3-01)

### 2026-10-26 — Người 1 (Claude Code) — order admin + đơn của khách (Phần 3/3)
- Đã làm: backend `GET /api/orders` (lọc, phân trang), `POST /api/orders/{id}/cancel` (`OrderService.cancelByCustomer`);
  `order/admin/` (`AdminOrderController`, `AdminOrderService`, `AdminOrderResponses`): `GET /api/admin/orders` (trạng thái, phương thức,
  ngày theo giờ VN, tìm mã đơn/SĐT/email/họ tên qua `UserService.findIdsByKeyword`), `GET /{id}` (+ `allowedStatuses`,
  `canCreateShipment`), `PATCH /{id}/status` (chỉ COD được xác nhận/huỷ tay, huỷ bắt buộc note, SHIPPING tự ghi mã vận đơn),
  `POST|GET /{id}/shipment` (tạo lại vận đơn GHN / xem hành trình). `@BatchSize` cho items/history. `UserService.profiles`.
  Frontend: `npm install` (antd/tiptap còn thiếu), `/admin/orders` (`OrderListView`), `/admin/orders/[id]` (`OrderDetailView`:
  nút theo `allowedStatuses`, modal ghi chú, vận đơn + hành trình GHN, lịch sử), `lib/orderStatus.ts` (nhãn dùng chung),
  tab "Đơn hàng của tôi" dùng API (`MyOrders`), `/account/orders/[id]` (`MyOrderDetail`, huỷ đơn); bỏ đơn mock (`OrderCard`,
  `Order`/`CartItem` trong `types/shop.ts`, `orders` trong ShopProvider). Docs: api.md 4.5/4.10, pages.md, user-flows mục 7.
- Kiểm chứng: compile + unit test xanh; `AdminOrderIntegrationTest` (5 ca) biên dịch được, CHƯA chạy (Docker tắt). Chạy thật trên DB tạm
  (đã xoá): 24/24 bước (list/huỷ của khách, lọc/tìm admin, máy trạng thái, COD → vận đơn GHN tự tạo, xem hành trình, hoàn tất → COD
  đã thu) + nhánh lỗi GHN (lỗi lưu lại → tạo lại 502 khi còn sai → sửa xong tạo lại OK). Sửa lỗi LazyInitialization ở tạo lại vận đơn.
  Vận đơn GHN thử nghiệm đã huỷ. Frontend: tsc + lint sạch; các trang mới trả 200 trên dev server.
- Còn dang dở / TODO tiếp theo:
  - Chạy `./scripts/agent-test.sh` khi bật Docker; thử trên trình duyệt (admin + employee + khách).
  - Trả hàng (FR-19), đánh giá, thông báo/email theo sự kiện đơn — module khác / Tier 2.
  - Chưa có webhook trạng thái GHN (nhân viên vẫn chuyển SHIPPING/COMPLETED tay, "Xem hành trình" đọc trực tiếp GHN).
- Plan liên quan: `.agents/plans/active/order-payment-shipping.md`

### 2026-10-26 — Người 1 (Claude Code) — cart, order, payment, inventory (Phần 2/3: đơn hàng + VNPay)
- Đã làm: `V5__order_schema.sql` (7 bảng mua hàng + cột GHN trên `orders`; `voucher_id` chưa FK). Viết lại `cart`/`order` (xoá code
  khung lệch docs + `OrderServiceIT` rỗng). `InventoryService` (khoá theo thứ tự, reserve/commit/release, refresh sau khoá),
  `CartService`/`CartController`, `OrderService` (preview + create đúng database.md mục 6; hỏi GHN trước khi khoá),
  `OrderStateService` (nơi duy nhất đổi trạng thái), `OrderQueryService`, `OrderTimeoutJob` (BR-3), `OrderShipmentService` +
  `OrderShipmentListener` (vận đơn GHN @Async sau commit khi CONFIRMED). `payment`: `Payment`, `PaymentGateway`, `VnpayService`
  (2.1.0, HMAC-SHA512, ×100), `PaymentResultService.handlePaymentResult` (Return URL + IPN), `VnpayController`
  (`/api/vnpay/return|ipn`). `promotion`: `PricingService` (giá = giá gốc), `VoucherService` (mọi mã INVALID_VOUCHER) làm điểm cắm.
  Cấu hình deploy: `VNP_*`, `APP_CORS_ALLOWED_ORIGINS`, `APP_FRONTEND_URL`, `PORT`, `forward-headers-strategy`.
  Frontend: `ShopProvider` dùng giỏ API, `cartService`/`orderService`, `CartPage` tách `CartLines`/`DeliveryInfoModal`/`useOrderPreview`,
  đặt hàng thật (PRICE_CHANGED hỏi xác nhận), `/checkout/success` đọc đơn thật, route `/payment/vnpay-return`, nút mua ở trang combo.
  Docs: D-28, api.md, database.md, architecture, pages, user-flows, features FR-11, known-issues KI-07..09, deployment, `.env.example`.
- Kiểm chứng: unit test xanh (`VnpayServiceTest`, `OrderStatusTest`, GHN/shipping/config/error code); `OrderPaymentFlowIntegrationTest`
  (9 ca) biên dịch được nhưng CHƯA chạy (Docker tắt). Chạy thật trên DB tạm `campvivo_verify` (đã xoá): `ddl-auto=validate` OK; 26/26
  bước end-to-end (giỏ, preview, PRICE_CHANGED, COD, VNPay URL được sandbox chấp nhận, Return/IPN thành công/lặp/sai chữ ký/sai tiền/
  thất bại, vận đơn GHN tự tạo); 10 khách đặt song song hàng còn 3 → đúng 3 đơn, không deadlock; job hết hạn huỷ đơn + trả giữ chỗ.
  Vận đơn GHN thử nghiệm đã huỷ. Frontend: lint + tsc sạch (trừ admin thiếu antd), các trang trả 200.
- Còn dang dở / TODO tiếp theo:
  - Chạy `./scripts/agent-test.sh` khi bật Docker. Thử trên trình duyệt (đăng nhập khách → giỏ → đặt COD/VNPay với thẻ test).
  - DB `campvivo` của người dùng sẽ chạy V5 khi khởi động lại backend.
  - Phần 3: `GET /orders` + huỷ đơn (khách, tab đơn hàng /account đang còn mock), admin/employee `/admin/orders` (lọc, chi tiết,
    đổi trạng thái, xác nhận COD → tạo vận đơn, tạo lại vận đơn khi lỗi).
- Plan liên quan: `.agents/plans/active/order-payment-shipping.md`

### 2026-10-26 — Người 1 (Claude Code) — shipping (Phần 1/3: GHN)
- Đã làm: nhánh `feature/order-payment-service`. Module backend `shipping`: `GhnService` (tỉnh/quận/phường có cache, gói dịch vụ,
  tính phí, tạo vận đơn, chi tiết vận đơn), `ShippingService` (cân = số món × `app.ghn.default-weight`, miễn phí từ
  `app.shipping.free-threshold`), `ShippingController` `/api/shipping/*` (công khai). `app.ghn.*` ← `GHN_*`; backend nạp `.env`
  gốc qua `spring.config.import`, tự bỏ nháy ở giá trị chuỗi. Mã lỗi `SHIPPING_GATEWAY_ERROR` (502). Bỏ `app.shipping.fee`.
  Frontend: `services/shippingService.ts`, `types/api/shipping.ts`, `AddressSelects` (3 dropdown GHN), `useShippingFee` (tự tính lại
  khi đổi địa chỉ/số món/giá trị đơn); `/cart` dùng 2 thứ này, xoá `geo.ts`. Docs: D-27, `api.md` 4.12, tech-stack, `.env.example`,
  local-development. Plan: `.agents/plans/active/order-payment-shipping.md`.
- Kiểm chứng: `GhnServiceTest` (HTTP server giả), `ShippingServiceTest`, `AppPropertiesTest`, `ErrorCodeTest` xanh; lint frontend sạch,
  tsc sạch ở file đã sửa (lỗi còn lại do `node_modules` thiếu antd/tiptap — cần `npm install`). Smoke test với GHN staging thật:
  tỉnh/quận/phường, gói dịch vụ, phí ship, miễn phí, địa chỉ sai → 502. Đã tạo + huỷ 1 vận đơn thử trên staging (`L8EAQ4`).
- Còn dang dở / TODO tiếp theo:
  - `ShippingControllerTest` CHƯA chạy được (Docker Desktop tắt — Testcontainers).
  - Chưa thử trên trình duyệt (người dùng test theo checklist).
  - Phần 2: F-08 migration + giỏ/đơn hàng API + VNPay (`handlePaymentResult` cho Return URL + IPN) + tạo vận đơn GHN. Phần 3: quản lý đơn.
- Plan liên quan: `.agents/plans/active/order-payment-shipping.md`

### 2026-10-24 — Người 3 — docs/config (hướng dẫn chạy local để đăng nhập được)
- Đã làm: nhánh `docs/local-dev-setup`. `application-dev.yml.example` đổi mặc định `ddl-auto: validate` → `none` (kèm chú thích
  lý do và khi nào đổi lại), vì `validate` làm backend báo `missing table [...]` đến khi F-06 → F-09 merge và ai copy `.example`
  theo hướng dẫn cũ đều gặp lỗi. `local-development.md` thêm mục "Thiết lập lần đầu / sau khi pull code mới" (pull `dev`, reset DB
  `docker compose down -v`, tạo `application-dev.yml`, chạy backend kèm múi giờ, chèn 3 tài khoản mẫu, chạy frontend cổng 3000).
- Còn dang dở / TODO tiếp theo:
  - Khi F-06 → F-09 đã vào `dev`: đổi `.example` về `validate` (hoặc xoá dòng) và cập nhật lại mục trên.
  - Làm F-12 (seed) thì bỏ bước chèn tay tài khoản mẫu.

### 2026-10-24 — Người 3 — frontend (đăng nhập thật + điều hướng theo role)
- Đã làm: nhánh `feature/frontend-auth-login-redirect`. `AuthProvider`/`useAuth` (khôi phục phiên bằng `GET /users/me`,
  trạng thái `loading|authenticated|unauthenticated|error`, đồng bộ giữa các tab, nối `setUnauthorizedHandler`), `authService`,
  trang `/login` (`LoginForm`, `components/ui/Button`, `Input` — hai component dùng chung đầu tiên), `lib/auth/redirect.ts`
  (`safeRedirectPath` chặn open redirect, `canAccessPath`, `landingFor`, `resolveLanding`, `loginHref`). Điều hướng: có `?redirect`
  hợp lệ và role được vào → đó; không thì ADMIN `/admin`, EMPLOYEE `/admin/orders`, CUSTOMER `/`. Admin: thay `useAdminSession`
  giả lập bằng phiên thật (xoá ô "Dev", bật Đăng xuất, chưa đăng nhập → `/login?redirect=`, lỗi mạng → "Thử lại").
  Storefront: bỏ `LoginPopup` OTP giả và login giả trong `ShopProvider` (`user` lấy từ phiên thật, `openLogin` → `/login`).
  Sửa `apiClient`: thêm `skipAuth` (endpoint xác thực công khai không gắn token — token cũ còn sót chặn đăng nhập).
  Docs: `pages.md`, `authentication.md`, `known-issues.md` (KI-06 đã xử lý), `lessons-learned.md`; bỏ `NEXT_PUBLIC_ADMIN_DEV_ROLE`.
- Kiểm chứng: lint, tsc, build xanh; script tạm ngoài repo: 12 nhóm kiểm tra hàm điều hướng (18 dạng URL độc hại bị chặn),
  13 ca luồng thật với backend (3 tài khoản mẫu: đăng nhập, `/users/me`, đăng xuất, sai mật khẩu, validate, `?redirect`),
  2 ca hồi quy token cũ; server Next thật phục vụ `/login`, `/admin`, `/`, `/account`, `/cart` (200); CSS storefront không lẫn vào admin.
- Còn dang dở / TODO tiếp theo:
  - CHƯA kiểm tra trên trình duyệt thật (bố cục `/login`, mobile 375px, thao tác click) — người dùng kiểm theo checklist.
  - `EMAIL_NOT_VERIFIED` chỉ hiện thông báo; tự gửi lại mã + chuyển `/verify-email` làm ở N3-02. Chưa có liên kết "Tạo tài khoản",
    "Quên mật khẩu?" (trang chưa có). Nút "Tạo tài khoản" ở popover Header hiện cũng dẫn tới `/login`.
  - `/api/admin/**` ở backend vẫn cho EMPLOYEE vào mọi đường dẫn admin; frontend đã chặn menu theo role nhưng backend phải chặn thật.
  - Endpoint xác thực công khai mới (register, verify-email, resend-code, forgot/reset-password) phải gọi với `{ skipAuth: true }`.
  - Giỏ hàng/đơn/yêu thích của storefront vẫn là mock (Người 1); `user` giả cũ trong localStorage bị bỏ qua.
  - Chuyển giữa storefront và admin tải lại toàn trang nên phiên được khôi phục lại bằng `GET /users/me` mỗi lần.
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (nối frontend auth, tuần 7)

### 2026-10-23 — Người 3 — backend (N3-03: đăng nhập/đăng xuất + GET /users/me)
- Đã làm: nhánh `feature/auth-n3-03-login`. `POST /auth/login` (sai email và sai mật khẩu trả CÙNG `INVALID_CREDENTIALS`,
  BCrypt giả cho email không tồn tại để chống dò email qua độ trễ; chỉ khi mật khẩu đúng mới báo `EMAIL_NOT_VERIFIED` /
  `ACCOUNT_DISABLED`), `POST /auth/logout` (tăng `token_version` bằng UPDATE nguyên tử), `GET /users/me`
  (`UserService`, `UserController`), bean `PasswordEncoder` BCrypt strength 10. Test `AuthLoginIntegrationTest`
  (14, PostgreSQL thật); sửa `SecurityIntegrationTest` (đường dẫn thử đổi sang `/api/notifications` vì `/users/me` đã có controller).
  Kiểm tay trên app thật với 3 tài khoản mẫu: đúng role, đúng quyền, logout làm token cũ 401.
  Docs: `local-development.md` (chèn tay tài khoản mẫu), D-19, `troubleshooting.md`, `lessons-learned.md`.
- Còn dang dở / TODO tiếp theo:
  - Làm N3-03 TRƯỚC N3-01/N3-02 (roadmap ghi phụ thuộc N3-02 nhưng không phụ thuộc kỹ thuật; test tạo user bằng repository).
    Ưu tiên của buổi: điều hướng theo role cho trang admin/staff. N3-01 (OTP + mail), N3-02, N3-04 làm sau.
  - N3-05 mới có phần `GET /users/me`; chưa có `PUT /users/me` và `PUT /users/me/password` → KHÔNG tick N3-05.
  - Chưa có giới hạn số lần đăng nhập sai (docs không yêu cầu); cân nhắc rate limit trước khi triển khai thật.
  - Tài khoản mẫu chèn tay (D-19) — F-12 chính thức làm sau khi F-06 → F-09 merge.
  - `UserController` import `CurrentUserId` của module `auth` (annotation dùng chung); có thể chuyển sang `common` nếu cần.
  - Tiếp theo: PR frontend (AuthProvider, `/login`, `landingFor(role, redirect)`, bảo vệ layout admin; thay `useAdminSession` — KI-06).
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (N3-03)

### 2026-10-23 — Người 3 — frontend (F-14: nền gọi API)
- Đã làm: nhánh `feature/frontend-f-14-api-client`. `services/apiClient.ts` (axios, tự gắn token, bóc `{success,data}`,
  `getPage` cho danh sách phân trang, lỗi → `ApiError`, 401 `UNAUTHORIZED` → xoá token + `setUnauthorizedHandler`;
  401 `INVALID_CREDENTIALS` KHÔNG bị coi là hết phiên), `services/serverApi.ts` (`serverGet`/`serverGetPage` cho SSR,
  chỉ endpoint công khai), `services/apiError.ts`, `services/apiConfig.ts`, `lib/authToken.ts` (key `campvivo_token`),
  `lib/errorMessages.ts` (39 mã backend khớp `ErrorCode.java` + `NETWORK_ERROR`/`UNKNOWN_ERROR`, `getErrorMessage`),
  bổ sung kiểu `OrderStatus`, `PaymentMethod`, `PaymentStatus`, `ReturnStatus`, `NotificationType`, `UserProfile`,
  `ApiSuccessBody` vào `types/api/common.ts`. Xoá `lib/api-client.ts` (bản khung chưa ai dùng, thay bằng bản mới).
- Kiểm chứng: lint, tsc, build xanh. Frontend chưa có test runner nên kiểm bằng script tạm ngoài repo (tsx): 16 ca
  với máy chủ giả + 5 ca với backend thật (401/404/mất kết nối/serverGet) đều đạt.
- Còn dang dở / TODO tiếp theo:
  - Handler 401 mặc định chỉ xoá token; chuyển về `/login?redirect=...` do `AuthProvider` đăng ký qua
    `setUnauthorizedHandler` (task nối phiên thật, thay `useAdminSession` giả lập — KI-06).
  - Bảng `errorMessages.ts` chép tay từ `ErrorCode.java`: thêm mã mới ở backend thì thêm ở đây.
  - Chưa có API đăng nhập (N3-03) và `GET /users/me` (N3-05); `catalogService` vẫn đọc mock (N2-04).
  - Tiếp theo: N3-01 (OTP + mail), N3-02, N3-03, N3-05 (`GET /users/me`), rồi task điều hướng theo role + trang `/login`.
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (F-14)

### 2026-10-23 — Người 3 — backend (F-10: security khung)
- Đã làm: nhánh `feature/auth-f-10-security`. `JwtService` (HS256, claim sub/role/tv, dùng `Clock`),
  `JwtAuthenticationFilter` (kiểm `token_version`, `is_active`, role lấy từ DB; principal = userId `Long`),
  `SecurityConfig` (phân quyền theo path như `authentication.md` mục 5, stateless, `@EnableMethodSecurity`),
  `JsonSecurityErrorHandler` (401/403 trả `ApiErrorResponse`), CORS theo `app.cors.allowed-origins`,
  `@CurrentUserId`. Xoá `TemporarySecurityConfig`. Test: `JwtServiceTest` (7), `SecurityIntegrationTest` (13, DB thật).
- Còn dang dở / TODO tiếp theo:
  - Controller của module khác dùng `@CurrentUserId Long userId` (hoặc `@AuthenticationPrincipal Long`, đang dùng ở
    `OrderController`); endpoint chỉ dành Admin phải thêm `@PreAuthorize("hasRole('ADMIN')")`. Endpoint mới phải thêm vào
    ma trận phân quyền N3-10.
  - Swagger (`/swagger-ui.html`, `/v3/api-docs`) đang mở công khai để cả nhóm thử API — đóng/giới hạn khi triển khai.
  - Đường dẫn mới ngoài danh sách trong `SecurityConfig` mặc định cần đăng nhập (401 với khách); endpoint công khai mới
    (GET) phải thêm vào `SecurityConfig`.
  - STOMP `/ws/**` đang permitAll ở tầng HTTP; xác thực JWT khi CONNECT làm ở N3-08.
  - Tiếp theo: F-14 (apiClient FE), F-12 (seed), F-11 (cả nhóm), rồi N3-01 (OTP).
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (F-10)

### 2026-10-23 — Người 3 — backend (F-05: bảng users)
- Đã làm: nhánh `feature/user-f-05`. `V1__users.sql` (16 cột theo `database.md` 3.1, CHECK `role`/`otp_purpose`),
  entity `User` (email tự chuẩn hoá chữ thường), `Role`, `OtpPurpose`, `UserRepository.findByEmail`. Xoá
  `V1__init_tier1.sql`, `V2__tier2.sql` (placeholder, D-14). `UserRepositoryTest` (5 test, PostgreSQL thật,
  `ddl-auto=validate`). Nâng Testcontainers 1.19.8 → 1.21.4 (bản cũ không kết nối được Docker Desktop 29, API ≥ 1.44).
  Docs: `database.md` mục 1, D-14, KI-04.
- Còn dang dở / TODO tiếp theo:
  - Ai pull nhánh này: chạy `mvn clean` một lần (target/ còn migration cũ gây "more than one migration with version 1")
    và reset DB local (`docker compose down -v` rồi `up -d`) vì lịch sử Flyway đổi.
  - `ddl-auto=validate` vẫn lỗi cho đến khi F-06 (N2) và F-08 (N1) tạo các bảng còn lại; tạm `ddl-auto: none` ở file dev.
  - Tiếp theo: F-10 (security, xoá `TemporarySecurityConfig`), F-14 (apiClient FE), F-12 (seed), F-11 (cả nhóm).
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (F-05)

### 2026-10-22 — Người 3 — backend (nền móng: F-01, F-02, F-03)
- Đã làm: nhánh `feature/common-foundation`.
  - F-01: thêm dependency jjwt, websocket, thymeleaf, OWASP sanitizer, cloudinary, springdoc 2.6.0
    (khớp Spring Boot 3.3), greenmail (test). `TemporarySecurityConfig` mở Swagger — XOÁ ở F-10.
  - F-02: `ErrorCode` (39 mã, khớp `api.md` mục 2, có test đối chiếu docs), `BusinessException(ErrorCode, details)`,
    `GlobalExceptionHandler` (validate → `fieldErrors`, 401/403, 500 không lộ chi tiết), `PageResponse`, `Money`,
    `SlugUtil`, `HtmlSanitizer`, bean `Clock` (Asia/Ho_Chi_Minh), `RequestIdFilter` (X-Request-Id → MDC/log).
    Thêm mã `INTERNAL_ERROR` (500) vào `api.md`.
  - F-03: `AppProperties` (`app.*`, secret JWT bắt buộc ≥ 32 ký tự), `application.yml`
    (`ddl-auto=validate`, `open-in-view=false`), `.env.example` thêm `NEXT_PUBLIC_WS_URL`.
- Còn dang dở / TODO tiếp theo:
  - `ddl-auto=validate` khiến app không khởi động đến khi F-05 xong (entity lệch migration placeholder, KI-04);
    tạm dùng `ddl-auto: none` trong `application-dev.yml` local.
  - Máy Windows có JVM timezone `Asia/Saigon` → PostgreSQL từ chối; chạy với `JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Ho_Chi_Minh`.
  - `OrderServiceIT` hiện không chạy trong `mvn test` (tên `*IT`, chưa có failsafe) — nhờ N4 (F-04) xử lý.
  - `BusinessException(String, String, HttpStatus)` là `@Deprecated`; `ProductService`, `OutOfStockException`
    cần chuyển sang `ErrorCode` (N2, N1). DTO phải ghi message validate bằng tiếng Việt.
  - Test tích hợp sau này phải cấp `app.jwt.secret` (CI chưa có `JWT_SECRET`).
  - Tiếp theo: F-05 (bảng users), F-10 (security), F-14 (apiClient FE), F-12 (seed).
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (F-01 → F-03)

### 2026-10-17 — Nguyen Khai Hung — frontend (khung admin)
- Đã làm: tách root layout `app/(store)` (storefront, CSS `styles/shop` chuyển lên layout này) và `app/(admin)`
  (Ant Design 5, locale vi_VN); `AdminShell` với sidebar theo role (`lib/admin/nav.ts`), `RequireRole`, trang
  "đang xây dựng" cho mục chưa làm, 404 riêng cho admin và storefront (catch-all); `types/api/common.ts`,
  `types/api/catalog.ts`. Docs: D-18, architecture, tech-stack, ui-guidelines, pages, api (mục 4.11), KI-06.
- Kiểm tra: lint, tsc, build xanh; chạy thử storefront + admin (ADMIN/EMPLOYEE/CUSTOMER) và chụp màn hình.
- TODO: phiên admin đang giả lập (KI-06) — nối auth thật khi có F-14/N3-03; tiếp theo là BE catalog
  (migration V2__catalog, API danh mục/sản phẩm) và trang /admin/categories, /admin/products.
- Plan liên quan: `.agents/plans/active/backend-roadmap.md` (N2-17, N2-01, N2-02, N2-18 → N2-21)

### 2026-10-17 — Nguyen Khai Hung — docs (đồng bộ theo báo cáo Phase1_v4)
- Đã làm: viết lại bộ docs theo báo cáo Phase1_v4 (5 phân hệ, 6 tác nhân, 22 bảng):
  - `decisions.md` D-00…D-17 (bỏ cho thuê, không sổ địa chỉ, combo tồn kho riêng, giỏ không chốt giá,
    SupportMessage thêm `is_from_staff`, VNPay + COD, OTP email, `token_version`, schema PostgreSQL 22 bảng…).
  - `features.md` (FR-01…FR-28 + luồng ngoại lệ), `use-cases.md` mới (use case → FR → API → route → bảng).
  - `database.md` (đặc tả từng cột, BR-1…BR-14, máy trạng thái, thứ tự khoá), `api.md` (toàn bộ
    endpoint, mã lỗi, kiểu TS), `authentication.md`, `architecture.md`, `tech-stack.md`,
    `coding-conventions.md`, `pages.md`, `user-flows.md`, `01-tong-quan/*`, vận hành, known-issues.
  - `.agents/rules`, skills, `AGENTS.md`, README, PR template.
- Còn dang dở / TODO tiếp theo:
  - Nhóm xác nhận: giữ COD, đăng xuất bằng `token_version`, phân công 4 người, phí ship, số ngày trả hàng.
  - Sửa báo cáo Phase1_v5 cho khớp docs (cột bổ sung ở `database.md` mục 2, use case "Process return
    request", chat với nhân viên thay chatbot AI…).
  - Viết migration đầy đủ 22 bảng thay V1/V2 placeholder: `.agents/plans/active/backend-schema-v1.md`.
  - Code backend hiện có lệch docs (KI-04); frontend mock lệch schema (KI-05).
- Plan liên quan: `.agents/plans/active/backend-schema-v1.md`, `.agents/plans/active/frontend-shop-ui.md`

### 2026-10-08 — Nguyen Khai Hung — frontend (ẩn nội dung mock của bên thứ ba)
- Đã làm:
  - `src/services/mockContent.ts` (`cleanMock`, gọi trong `readJson` của `catalogService`) xử lý chữ nhắc tên và hotline của cửa hàng tham khảo trong dữ liệu mock:
    - tiêu đề: bỏ nhãn;
    - đoạn ngắn: ẩn;
    - nội dung dài (bài viết, mô tả sản phẩm, chính sách): thay bằng ô "Nội dung đang được cập nhật".
  - Không sửa file mock. Dữ liệu do nhóm tự viết sẽ không bị ảnh hưởng.
  - `/guides` có phần đầu trang Campvivology riêng.
  - Bỏ banner showroom (ảnh có in chữ thương hiệu) ở trang chi tiết bài viết.
- TODO: thay nội dung, ảnh mock bằng dữ liệu của nhóm. Bộ lọc không xử lý được chữ in trong ảnh (banner, ảnh sản phẩm).

### 2026-10-08 — Nguyen Khai Hung — frontend (New arrivals + Outdoor Journal)
- Đã làm:
  - New arrivals: `/categories/san-pham-moi`, có banner (trường `banner` của Listing) và 119 sản phẩm.
  - Outdoor Journal: `/journal` (phân trang, 10 danh mục, 102 bài chi tiết trong `src/mocks/data/journal/`). CSS riêng `styles/shop/journal.css` (`.pg-journal`). Tái sử dụng `ArticleList` / `ArticleDetail` (thêm tham số `hrefFor`, `breadcrumbs`, `wrapperClass`).
  - Link "New arrivals" và "Outdoor Journal" ở header và footer đã nối vào các trang mới.
- Còn dang dở: mới có 5 trang danh sách journal (45 bài) và trang đầu của mỗi danh mục. Bộ lọc "15 đỉnh núi" và "Chủ đề HOT" mới chỉ bật/tắt được, chưa lọc.

### 2026-10-08 — Nguyen Khai Hung — frontend (storefront UI)
- Đã làm: đưa giao diện cửa hàng desktop vào `frontend/` trên nhánh `feature/frontend-ui-shop` (tách từ `dev`). Gồm:
  - các trang: trang chủ, tìm kiếm, danh mục, chi tiết sản phẩm, giỏ hàng + thanh toán mock, tài khoản, thương hiệu, blog, trang chính sách, khuyến mãi;
  - route theo `docs/03-san-pham/pages.md` (đã bổ sung route mới vào đó);
  - dữ liệu mock ở `src/mocks/data`, đọc qua `src/services/catalogService.ts`;
  - giỏ hàng, đăng nhập OTP giả, đơn hàng lưu localStorage (`components/features/shop/ShopProvider.tsx`).
- Kiểm tra: lint, typecheck và build (Next 14.2.5) đều qua; đã chạy thử từng route và luồng mua hàng.
- Còn dang dở / TODO tiếp theo:
  - Nối API backend: thay `catalogService` và `ShopProvider` bằng `services/<module>Service.ts`.
  - Giao diện mobile.
  - Trang `/login`, `/register` riêng.
  - Thay nội dung, ảnh mẫu bằng dữ liệu thật của Campvivo. Ảnh mẫu đang tải từ host bên ngoài.
  - Các link `data-dead` (liên hệ, hỏi đáp, cộng đồng, mạng xã hội) chưa có trang.
- Plan liên quan: `.agents/plans/active/frontend-shop-ui.md`

