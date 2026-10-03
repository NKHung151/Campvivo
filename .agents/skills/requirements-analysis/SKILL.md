---
name: requirements-analysis
description: Phân tích yêu cầu tính năng mới thành phạm vi rõ ràng, đối chiếu với SRS và
  quy tắc nghiệp vụ đã có trong docs/, trước khi lập kế hoạch implement.
---

# Requirements Analysis

## Khi nào dùng
Trước khi thêm tính năng mới, hoặc khi yêu cầu của người dùng mơ hồ/thiếu chi tiết.

## Quy trình
1. Đối chiếu yêu cầu với `docs/03-san-pham/features.md` (mã FR-xx) — tính năng đã có trong
   SRS chưa, thuộc tier nào.
2. Nếu là yêu cầu hoàn toàn mới: xác định actor liên quan (`docs/03-san-pham/personas.md`),
   viết lại thành use case ngắn (pre-condition, main flow, exception flow) theo đúng format
   đã dùng trong `docs/01-tong-quan/`.
3. Kiểm tra quy tắc nghiệp vụ liên quan trong `docs/02-ky-thuat/database.md` (vd. công thức
   tồn kho, máy trạng thái đơn hàng) — không được mâu thuẫn.
4. Liệt kê rõ: API nào cần thêm/sửa, bảng nào cần thêm/sửa, trang nào bị ảnh hưởng.
5. Nếu yêu cầu không rõ thuộc Tier 1/2/3 → hỏi lại người dùng, KHÔNG tự giả định là Tier 1.

## Tiêu chí hoàn thành
- Có danh sách rõ ràng: API, DB, UI bị ảnh hưởng + tier.
- Không mâu thuẫn với quy tắc nghiệp vụ đã chốt.
