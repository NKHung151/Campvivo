# Quyết định kỹ thuật (Architecture Decision Log)

Ghi lại mọi quyết định kỹ thuật/nghiệp vụ quan trọng để agent (và người) sau này không hỏi
lại hoặc vô tình làm ngược lại quyết định đã chốt.

## Mẫu
### <Ngày> — <Tên quyết định>
- **Bối cảnh:**
- **Quyết định:**
- **Lý do:**
- **Phương án khác đã cân nhắc:**

---

### 2025 (đầu dự án) — Chọn "bán là chính, thuê là phụ lục"
- **Bối cảnh:** Ý tưởng ban đầu là web cho thuê đồ dã ngoại thuần túy; sau đó cân nhắc theo
  mô hình WeTrek (vừa bán vừa thuê, nhiều biến thể, tích hợp vận chuyển thật).
- **Quyết định:** Tier 1 = bán hàng đầy đủ vòng đời; Tier 2 = các tính năng nâng cao
  (combo, khuyến mãi, đánh giá, thông báo, dashboard, gợi ý); Tier 3 = cho thuê giới hạn,
  chỉ mở sau mốc tuần 9 nếu Tier 1+2 ổn định.
- **Lý do:** Nhóm 4 người, 3 tháng, ưu tiên an toàn tiến độ. Làm cả mua lẫn thuê đồng thời
  với đầy đủ biến thể sản phẩm và tích hợp vận chuyển thật là quá tải cho quy mô đồ án.
- **Phương án khác đã cân nhắc:** Clone toàn bộ mô hình WeTrek (bán + thuê + biến thể +
  vận chuyển thật) — bị loại vì khối lượng nghiệp vụ gấp nhiều lần, rủi ro vỡ tiến độ cao.

### 2025 — Không dùng biến thể sản phẩm (variants)
- **Bối cảnh:** Tài liệu tham khảo (mô hình Campvivo mở rộng) có `product_variants`,
  `rental_variant_pricing` cho màu/size/chất liệu.
- **Quyết định:** Không tạo bảng biến thể riêng. Mỗi sản phẩm (vd. "Lều 2 người") là 1 bản
  ghi `products` độc lập.
- **Lý do:** Đồ dã ngoại cho thuê/bán (lều, bếp, túi ngủ) không cần biến thể phức tạp như
  quần áo/giày; thêm biến thể chỉ tốn effort mà không tăng điểm kỹ thuật tương xứng.

### 2025 — Không tích hợp API vận chuyển thật
- **Quyết định:** Mô phỏng trạng thái vận đơn (admin cập nhật thủ công
  CONFIRMED→SHIPPING→COMPLETED), không gọi API thật của đơn vị vận chuyển.
- **Lý do:** Tích hợp bên thứ ba rủi ro thời gian cao (cần đăng ký doanh nghiệp, chờ duyệt),
  không phải trọng tâm kỹ thuật của đồ án.
