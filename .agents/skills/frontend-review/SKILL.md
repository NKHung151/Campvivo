---
name: frontend-review
description: Kiểm tra lại giao diện đã code so với thiết kế và yêu cầu — responsive,
  accessibility, trạng thái biên.
---

# Frontend Review

## Khi nào dùng
Sau khi hoàn thành 1 trang/luồng UI, trước khi coi là "xong".

## Checklist
1. Khớp route và nội dung với `docs/03-san-pham/pages.md`, `features.md`.
2. Responsive: không vỡ layout ở ~375px, ~768px, ~1280px.
3. Có đủ loading/empty/error state, không để trắng trang hoặc console error khi API lỗi.
4. Accessibility cơ bản: alt cho ảnh sản phẩm, label cho input, contrast đủ đọc.
5. Không có `any` không giải thích, không còn `console.log` debug.
6. Nếu môi trường cho phép chạy trình duyệt/screenshot — kiểm tra bằng mắt, không chỉ đọc code.

## Tiêu chí hoàn thành
- Checklist trên pass hết, hoặc ghi rõ lý do bỏ qua mục nào.
