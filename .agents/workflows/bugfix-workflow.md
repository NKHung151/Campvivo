# Workflow: Sửa bug

1. **project-context** — đọc module liên quan, không giả định nguyên nhân khi chưa xem code.
2. **debugging** — tái hiện lỗi, xác định nguyên nhân gốc (xem đặc biệt phần lỗi tồn kho/
   trạng thái đơn/webhook trong `debugging/SKILL.md`).
3. Viết test tái hiện đúng lỗi TRƯỚC khi sửa (nếu có thể).
4. Sửa tại gốc nguyên nhân, không patch triệu chứng.
5. **testing-qa** — đảm bảo test mới pass và không phá test cũ.
6. Nếu lỗi do thiết kế/quy tắc nghiệp vụ chưa rõ ràng → ghi vào
   `docs/99-ghi-chu/known-issues.md` hoặc `decisions.md`.
7. **code-review** → **git-safety**, nhánh `fix/<module>-<mo-ta>`.
