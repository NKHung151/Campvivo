# Kiến trúc hệ thống

```
┌──────────────┐        REST API        ┌──────────────┐        ┌──────────────┐
│   Next.js    │ ─────────────────────► │  Spring Boot │ ─────► │  PostgreSQL  │
│   Frontend   │ ◄───────────────────── │   Backend    │ ◄───── │              │
└──────────────┘      JSON + JWT        └──────┬───────┘        └──────────────┘
                                                │
                              ┌─────────────────┼─────────────────┐
                              ▼                 ▼                 ▼
                        MoMo/VNPay API    SMTP (email)     Cloudinary (ảnh)
```

## Backend — phân module theo package (không theo layer)
Mỗi package dưới `vn.campvivo.*` là 1 module nghiệp vụ độc lập, tự chứa
Controller/Service/Repository/Entity/DTO của mình:

```
auth/ · user/ · catalog/ · inventory/ · cart/ · order/ · payment/
promotion/ · combo/ · review/ · notification/ · recommendation/
analytics/ · rental/ (Tier 3, để trống đến khi mở)
common/ (config, exception, dto dùng chung, util)
```

Luồng xử lý 1 request: `Controller` nhận & validate DTO → `Service`
(`@Transactional` khi cần) chứa nghiệp vụ → `Repository` (Spring Data JPA) truy
xuất DB. Entity không trả thẳng ra API.

## Frontend — phân theo feature
```
app/            Next.js App Router: (shop)/ (auth)/ account/ admin/
components/ui/        component dùng chung (Button, Modal, Toast...)
components/features/  component nghiệp vụ theo module (cart, order, product...)
services/              gọi API theo module
types/                  kiểu dữ liệu khớp docs/02-ky-thuat/api.md
```

## Nguyên tắc module hoá
- Module backend ↔ module frontend tương ứng 1-1 khi có thể (vd. `order` ↔
  `features/order`).
- Một module không gọi thẳng vào Repository của module khác — gọi qua Service public của
  module đó, để ranh giới module rõ ràng và dễ test độc lập.
- Đổi kiến trúc này cần thống nhất lại và ghi vào `docs/99-ghi-chu/decisions.md`.
