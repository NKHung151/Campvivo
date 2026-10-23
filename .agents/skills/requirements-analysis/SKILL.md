---
name: requirements-analysis
description: Phân tích yêu cầu tính năng thành phạm vi rõ ràng, đối chiếu với báo cáo Phase1
  (use case), FR và quy tắc nghiệp vụ đã có trong docs/, trước khi lập kế hoạch implement.
---

# Requirements Analysis

## Khi nào dùng
Trước khi thêm tính năng mới, hoặc khi yêu cầu của người dùng mơ hồ/thiếu chi tiết.

## Quy trình
1. Tra `docs/03-san-pham/use-cases.md` (tên use case theo báo cáo) và `features.md` (mã FR-xx,
   tier, mô tả chi tiết + luồng ngoại lệ). Tính năng đã có trong báo cáo chưa, thuộc tier nào.
2. Nếu là yêu cầu hoàn toàn mới (không có trong báo cáo): xác định actor (`personas.md`), viết lại
   thành use case ngắn (điều kiện trước, luồng chính, luồng ngoại lệ + mã lỗi) theo format ở
   `features.md`, đánh dấu "(bổ sung)" và đề nghị người dùng ghi quyết định vào `decisions.md`.
3. Kiểm tra quy tắc nghiệp vụ liên quan trong `docs/02-ky-thuat/database.md` (BR-1…BR-14, máy trạng
   thái mục 5, thứ tự khoá mục 6) và `decisions.md` — không được mâu thuẫn.
4. Liệt kê rõ: API nào cần thêm/sửa (`api.md`), bảng/cột nào cần thêm/sửa, trang nào bị ảnh hưởng
   (`pages.md`), sự kiện/thông báo nào phát ra.
5. Nếu yêu cầu không rõ thuộc Tier 1/2/3 → hỏi lại người dùng. Tier 3 (MoMo, chatbot AI) không làm nếu
   chưa được yêu cầu rõ.

## Tiêu chí hoàn thành
- Có danh sách rõ ràng: API, DB, UI bị ảnh hưởng + tier + use case/FR liên quan.
- Không mâu thuẫn với quy tắc nghiệp vụ và quyết định đã chốt.
