## Tóm tắt
<Mô tả ngắn gọn PR này làm gì>

## Module liên quan
- [ ] auth  - [ ] user  - [ ] catalog  - [ ] combo  - [ ] inventory  - [ ] cart
- [ ] order  - [ ] payment  - [ ] promotion  - [ ] review  - [ ] favorite  - [ ] article
- [ ] support  - [ ] notification  - [ ] recommendation  - [ ] analytics  - [ ] common
- FR / use case liên quan: ____________
- [ ] frontend (route/feature): ____________

## Loại thay đổi
- [ ] Tính năng mới
- [ ] Sửa bug
- [ ] Đổi schema DB (có migration mới, không sửa migration cũ)
- [ ] Đổi API contract (đã cập nhật `docs/02-ky-thuat/api.md`)
- [ ] Chỉ tài liệu/cấu hình

## Checklist trước khi xin review
- [ ] Đã đọc `AGENTS.md` và rule liên quan (`general.md` + rule theo stack)
- [ ] `./scripts/agent-lint.sh` và `./scripts/agent-test.sh` xanh
- [ ] Không có secret commit nhầm
- [ ] Đã cập nhật `docs/` nếu hành vi nghiệp vụ/API/schema thay đổi (skill `documentation-sync`)
- [ ] Nếu liên quan tồn kho/voucher/thanh toán/trạng thái đơn: có test nhánh chính + nhánh lỗi (+ race condition)
- [ ] Schema/endpoint/mã lỗi khớp `database.md` / `api.md`

## Cách test
<Các bước để reviewer tự kiểm tra>

## Ghi chú thêm
<Rủi ro, trade-off, việc còn lại...>
