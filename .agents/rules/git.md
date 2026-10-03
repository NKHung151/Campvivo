# Quy tắc Git

- Nhánh: `main` (ổn định) ← `dev` (tích hợp) ← `feature/<module>-<mo-ta>`
  (vd. `feature/order-reserve-stock`).
- Không push thẳng vào `dev` hoặc `main`. Mọi thay đổi qua Pull Request.
- PR cần ≥1 reviewer là chủ module liên quan (xem bảng phân công trong `AGENTS.md` mục 9)
  và CI phải xanh (build + lint) trước khi merge.
- Commit theo Conventional Commits: `feat(order): reserve stock on checkout`,
  `fix(cart): prevent negative quantity`, `docs(api): update promotion endpoint`.
- Không `force push` vào `dev`/`main`. Không rebase lịch sử đã merge.
- Không commit file `.env`, khóa API, hoặc dữ liệu test chứa thông tin thật.
- Agent coding KHÔNG tự ý merge PR vào `main` — chỉ chuẩn bị PR, việc merge do người quyết định.
