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
4. Logic tồn kho/thanh toán có transaction + lock đúng quy tắc trong `database.md`.
5. Không có code chết, `console.log`/`System.out.println` debug còn sót.
6. Test liên quan đã có và pass.
7. Đã cập nhật `docs/` nếu hành vi nghiệp vụ thay đổi.

## Tiêu chí hoàn thành
- Checklist pass hoặc có ghi chú rõ lý do bỏ qua mục nào, kèm người chịu trách nhiệm xử lý sau.
