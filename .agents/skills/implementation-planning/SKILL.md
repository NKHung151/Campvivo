---
name: implementation-planning
description: Lập kế hoạch implement trước khi code cho các thay đổi lớn (nhiều module,
  đổi schema DB, đổi API contract) — lưu vào .agents/plans/active/.
---

# Implementation Planning

## Khi nào dùng
Thay đổi > 1 module, thêm/sửa bảng DB, đổi API contract, hoặc bất kỳ việc nào ước tính
> ~2 giờ code.

## Quy trình
1. Copy `.agents/plans/_template.md` vào `.agents/plans/active/<ten-tinh-nang>.md`.
2. Điền: mục tiêu, phạm vi (tier), danh sách file sẽ tạo/sửa, thứ tự thực hiện, rủi ro
   (vd. ảnh hưởng tới luồng tồn kho đang chạy), cách test.
3. Nếu đổi API contract: cập nhật `docs/02-ky-thuat/api.md` TRƯỚC khi code backend/frontend,
   để 2 phía có thể làm song song dựa trên contract thống nhất.
4. Nếu đổi schema: viết trước nội dung file Flyway dự kiến trong plan.
5. Thực hiện theo đúng thứ tự đã lập kế hoạch. Nếu phát sinh thay đổi plan giữa chừng, cập
   nhật lại file plan, không âm thầm làm khác.
6. Khi xong, chuyển file từ `.agents/plans/active/` sang `.agents/plans/completed/`.

## Tiêu chí hoàn thành
- Plan phản ánh đúng những gì đã làm.
- File đã chuyển sang `completed/`.
