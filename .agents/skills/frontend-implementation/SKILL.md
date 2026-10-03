---
name: frontend-implementation
description: Hiện thực hóa UI bằng Next.js/React/TypeScript theo đúng thiết kế và API
  contract đã thống nhất.
---

# Frontend Implementation

## Khi nào dùng
Sau khi đã có thiết kế (skill `frontend-design`) hoặc với việc nhỏ không cần thiết kế riêng.

## Quy trình
1. Theo `.agents/rules/frontend.md`.
2. Đặt route đúng theo `docs/03-san-pham/pages.md`.
3. Kiểu dữ liệu lấy theo `docs/02-ky-thuat/api.md` — đặt trong `frontend/src/types/`.
4. Gọi API qua `services/<module>Service.ts`, không fetch trực tiếp trong component.
5. Nếu backend chưa xong, dùng dữ liệu giả trong `frontend/src/mocks/` để không bị block,
   ghi rõ TODO thay bằng API thật.
6. Component nghiệp vụ → `components/features/<module>/`; component dùng chung →
   `components/ui/`.

## Tiêu chí hoàn thành
- `npm run lint && npm run build` xanh.
- Đủ loading/empty/error state.
- Responsive đã kiểm tra mobile + desktop.
