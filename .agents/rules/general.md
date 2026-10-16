# Quy tắc chung (mọi stack)

1. Đọc `AGENTS.md` trước, xác định module bị ảnh hưởng và FR/use case liên quan
   (`docs/03-san-pham/features.md`, `use-cases.md`).
2. Không tạo chức năng trùng lặp — luôn grep/tìm code đã có trước khi viết mới.
3. File nhỏ, một trách nhiệm. Nếu 1 file > ~300 dòng, cân nhắc tách.
4. Không hardcode dữ liệu nghiệp vụ (thời hạn trả hàng, timeout thanh toán, thời hạn OTP, phí vận
   chuyển, ngưỡng sắp hết hàng...) — đọc từ cấu hình `app.*` (bảng key trong
   `docs/02-ky-thuat/tech-stack.md`) hoặc từ DB (vd. bảng `vouchers`, `discount_campaigns`).
5. Đặt tên theo đúng thuật ngữ đã dùng trong `docs/` (vd. luôn là `reservedQuantity`, `voucher`,
   `combo`, `isFromStaff`; không tự đổi thành `heldQuantity`, `promotionCode`, `bundle`).
6. Thay đổi > 1 module hoặc đổi API/DB contract → bắt buộc tạo plan trong
   `.agents/plans/active/` trước khi code (dùng `.agents/plans/_template.md`).
7. Không tự ý đổi kiến trúc hay quyết định đã chốt (`docs/02-ky-thuat/architecture.md`,
   `docs/99-ghi-chu/decisions.md`) — nếu thấy cần đổi, dừng lại và hỏi người dùng, ghi lý do vào
   `decisions.md` sau khi được đồng ý.
8. Docs là nguồn đúng. Code hiện có lệch docs (xem `docs/99-ghi-chu/known-issues.md` KI-04, KI-05)
   thì sửa code theo docs, không sửa docs theo code (trừ khi người dùng yêu cầu).
9. Không thêm bảng ngoài 22 bảng của báo cáo; không thêm trạng thái, mã lỗi, endpoint mà chưa cập
   nhật `database.md`/`api.md`.
10. Không commit secrets. Dùng biến môi trường, tham chiếu `.env.example`.
