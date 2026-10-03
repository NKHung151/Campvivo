# Coding Conventions

## Backend (Java/Spring Boot)
- Package theo module nghiệp vụ, không theo layer toàn cục (xem `architecture.md`).
- Naming: Entity số ít (`Product`), Repository `<Entity>Repository`, Service `<Entity>Service`
  hoặc theo use case (`OrderService.createOrder`), Controller `<Module>Controller`.
- DTO request/response riêng, không expose Entity qua API.
- Exception nghiệp vụ kế thừa `RuntimeException`, có `errorCode` khớp với `docs/02-ky-thuat/
  api.md`, xử lý tập trung ở `common/exception/GlobalExceptionHandler`.
- Trạng thái (`OrderStatus`, `PaymentStatus`...) dùng enum Java + `@Enumerated(STRING)`.

## Frontend (Next.js/TypeScript)
- Component function, đặt tên PascalCase; hook bắt đầu bằng `use`.
- Service gọi API đặt trong `services/`, trả Promise kiểu đã định nghĩa trong `types/`.
- Không dùng `any` nếu không giải thích; ưu tiên kiểu rõ ràng khớp `api.md`.
- Style: theo `docs/03-san-pham/ui-guidelines.md`, tái sử dụng `components/ui/`.

## Chung
- Commit theo Conventional Commits (xem `.agents/rules/git.md`).
- Không hardcode dữ liệu nghiệp vụ (giá, %, số ngày) — xem `.agents/rules/general.md`.
