## Tóm tắt
<Mô tả ngắn gọn PR này làm gì>

## Module liên quan
- [ ] auth  - [ ] user  - [ ] catalog  - [ ] inventory  - [ ] cart  - [ ] order
- [ ] payment  - [ ] promotion  - [ ] combo  - [ ] review  - [ ] notification
- [ ] recommendation  - [ ] analytics  - [ ] rental (Tier 3)
- [ ] frontend (route/feature): ____________

## Loại thay đổi
- [ ] Tính năng mới
- [ ] Sửa bug
- [ ] Đổi schema DB (có migration mới, không sửa migration cũ)
- [ ] Đổi API contract (đã cập nhật `docs/02-ky-thuat/api.md`)
- [ ] Chỉ tài liệu/cấu hình

## Checklist trước khi xin review
- [ ] Đã đọc `AGENTS.md` và rule liên quan (`general.md` + rule theo stack)
- [ ] `mvn test` xanh (nếu đụng backend) / `npm run lint && npm run build` xanh (nếu đụng frontend)
- [ ] Không có secret commit nhầm
- [ ] Đã cập nhật `docs/` nếu hành vi nghiệp vụ/API/schema thay đổi (skill `documentation-sync`)
- [ ] Nếu liên quan tồn kho/thanh toán/trạng thái đơn: có test cho nhánh chính + nhánh lỗi

## Cách test
<Các bước để reviewer tự kiểm tra>

## Ghi chú thêm
<Rủi ro, trade-off, việc còn lại...>
