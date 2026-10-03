# Quy tắc chung (mọi stack)

1. Đọc `AGENTS.md` trước, xác định module bị ảnh hưởng.
2. Không tạo chức năng trùng lặp — luôn grep/tìm code đã có trước khi viết mới.
3. File nhỏ, một trách nhiệm. Nếu 1 file > ~300 dòng, cân nhắc tách.
4. Không hardcode dữ liệu nghiệp vụ (giá, % cọc, số ngày phạt...) — đặt làm hằng số/config
   có tên rõ nghĩa, lý tưởng là đọc từ DB/entity (vd. bảng `promotions`, `rental_penalties`).
5. Đặt tên theo đúng thuật ngữ đã dùng trong `docs/` (vd. luôn là `reservedQuantity`,
   không tự đổi thành `heldQuantity`).
6. Thay đổi > 1 module hoặc đổi API/DB contract → bắt buộc tạo plan trong
   `.agents/plans/active/` trước khi code (dùng `.agents/plans/_template.md`).
7. Không tự ý đổi kiến trúc đã chốt trong `docs/02-ky-thuat/architecture.md` — nếu thấy cần
   đổi, dừng lại và hỏi người dùng, ghi lý do vào `docs/99-ghi-chu/decisions.md` sau khi được
   đồng ý.
8. Không commit secrets. Dùng biến môi trường, tham chiếu `.env.example`.
