---
name: code-review
description: Review chất lượng code, kiến trúc và bảo mật trước khi tạo/merge Pull Request.
---

# Code Review

## Khi nào dùng
Trước khi mở PR, hoặc khi được yêu cầu review code người khác/AI khác viết.

## Checklist
1. Đúng kiến trúc module (`docs/02-ky-thuat/architecture.md`), không rò rỉ logic nghiệp vụ
   vào Controller/Component.
2. Không có secret, key, mật khẩu hardcode.
3. Input từ client được validate (DTO backend, form frontend).
4. Logic tồn kho (sản phẩm + combo)/voucher/thanh toán có transaction + khoá đúng thứ tự
   `orders → products → product_combos → vouchers` (`database.md` mục 6); chuyển trạng thái qua
   enum máy trạng thái; IPN kiểm chữ ký + idempotent.
5. Giá chỉ tính qua `PricingService`; không lưu giá ở giỏ; tham số nghiệp vụ đọc từ `app.*`.
6. Quyền: endpoint có `@PreAuthorize`/rule path đúng; dữ liệu sở hữu lọc theo `userId` từ token
   (không từ request); không log mật khẩu/OTP/token.
7. Side-effect (thông báo/email) qua event AFTER_COMMIT, không gọi trực tiếp trong transaction.
8. Schema/entity khớp `database.md`; endpoint, field, mã lỗi khớp `api.md`.
9. Không có code chết, `console.log`/`System.out.println` debug còn sót.
10. Test liên quan đã có và pass.
11. Đã cập nhật `docs/` nếu hành vi nghiệp vụ thay đổi.

## Tiêu chí hoàn thành
- Checklist pass hoặc có ghi chú rõ lý do bỏ qua mục nào, kèm người chịu trách nhiệm xử lý sau.
