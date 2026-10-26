# Plan: Đặt hàng – thanh toán VNPay – giao hàng GHN

- **Ngày tạo:** 2026-10-26
- **Người/agent phụ trách:** Người 1 (Claude Code hỗ trợ), nhánh `feature/order-payment-service`
- **Tier:** 1
- **Module backend liên quan:** `shipping` (mới), `cart`, `order`, `payment`, `common`, `auth` (SecurityConfig)
- **Module/feature frontend liên quan:** `features/cart` (trang `/cart`), `/checkout/success`, `/payment/vnpay-return`,
  `/account` (đơn của tôi), admin `/admin/orders`

## Mục tiêu
Nối luồng mua hàng end-to-end của `user-flows.md` mục 4 vào giao diện có sẵn, thêm GHN (địa chỉ 3 cấp, phí ship thật,
tạo vận đơn) và VNPay sandbox. Bổ sung quản lý đơn cho Admin/Employee (FR-13).

## Phạm vi
- Trong phạm vi, chia phần — **xong mỗi phần dừng lại cho người dùng test**:
  1. **GHN:** `GhnService` (tỉnh, quận, phường, gói dịch vụ, tính phí, tạo đơn, xem chi tiết đơn), API
     `/api/shipping/*`, 3 dropdown Tỉnh/Quận/Phường ở `/cart`, phí ship tự tính lại khi đổi địa chỉ (D-27).
  2. **VNPay + đơn hàng:** migration `carts`…`payments` (F-08, có thêm cột GHN), giỏ hàng API, `POST /orders`,
     `VnpayService` (URL 2.1.0, HMAC-SHA512, amount×100, `vnp_TxnRef` duy nhất, verify), `handlePaymentResult`
     dùng chung cho Return URL và IPN `/api/vnpay/ipn` (D-28), gọi GHN tạo vận đơn khi thanh toán xong.
  3. **Quản lý đơn:** khách (`/account` đơn của tôi, huỷ), Admin/Employee `/admin/orders` + `/admin/orders/[id]`
     (lọc, chi tiết, đổi trạng thái, xem vận đơn GHN).
- Ngoài phạm vi: voucher/chiến dịch (module `promotion` của N4 chưa có — đơn không áp voucher cho tới khi có),
  trả hàng, thông báo/email, webhook trạng thái GHN.

## Thứ tự thực hiện
1. Phần 1 — `shipping` backend + frontend dropdown/phí ship. Docs: `api.md` 4.12, D-27, `tech-stack.md`.
2. Phần 2 — F-08 migration, cart, order, payment, VNPay; Return URL/IPN. Docs: D-28, `api.md`, `database.md`.
3. Phần 3 — admin/employee orders + đơn của khách.

## Ảnh hưởng tới hệ thống hiện có
- D-17 (phí ship cố định) và quyết định 2025 "Không tích hợp API vận chuyển thật" bị thay thế bởi D-27.
- `.env` ở gốc repo được backend nạp qua `spring.config.import` (biến `GHN_*`, `VNP_*`); key không bao giờ ra frontend.

## Cách test
- Phần 1: unit test `ShippingService` (cân nặng, miễn phí ship), test controller với `GhnService` giả; thử tay
  `/cart` chọn tỉnh → quận → phường, phí ship đổi theo địa chỉ.
- Phần 2–3: theo `.agents/rules/testing.md` (IPN lặp, sai chữ ký, sai số tiền; máy trạng thái).

## Trạng thái
- [x] Phần 1 GHN (code + test đơn vị xong, chờ người dùng test tay)
- [x] Phần 2 VNPay + đơn hàng (code + unit test + chạy thật trên DB tạm; IT chờ Docker; chờ người dùng test tay)
- [x] Phần 3 quản lý đơn (code + chạy thật trên DB tạm; IT chờ Docker; chờ người dùng test tay)
- [x] Đã cập nhật docs/
- [ ] Đã mở PR
