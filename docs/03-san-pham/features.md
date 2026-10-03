# Danh sách tính năng (Feature Requirements)

| Mã | Yêu cầu | Tier | Phụ trách |
|---|---|---|---|
| FR-01 | Đăng ký, đăng nhập (JWT, BCrypt) | 1 | Người 3 |
| FR-02 | Xem/sửa hồ sơ, đổi mật khẩu | 1 | Người 3 |
| FR-03 | Quản lý sổ địa chỉ, đặt mặc định | 1 | Người 3 |
| FR-04 | Tìm kiếm, lọc, sắp xếp, phân trang sản phẩm | 1 | Người 3 |
| FR-05 | CRUD danh mục đa cấp | 1 | Người 2 |
| FR-06 | CRUD sản phẩm, upload nhiều ảnh | 1 | Người 2 |
| FR-07 | Quản lý tồn kho (stock/reserved/available) | 1 | Người 2 |
| FR-08 | Giỏ hàng: thêm/sửa/xóa, chốt giá tại thời điểm thêm | 1 | Người 1 |
| FR-09 | Tạo đơn hàng, giữ chỗ tồn kho (lock) | 1 | Người 1 |
| FR-10 | Thanh toán MoMo/VNPay/COD, webhook idempotent | 1 | Người 1 |
| FR-11 | Theo dõi đơn, hủy đơn khi PENDING | 1 | Người 1 |
| FR-12 | Admin/Employee duyệt đơn, đổi trạng thái, ghi lịch sử | 1 | Người 2 |
| FR-13 | Mã khuyến mãi: tạo, kiểm tra, áp vào đơn | 2 | Người 4 |
| FR-14 | Combo sản phẩm giá ưu đãi | 2 | Người 4 |
| FR-15 | Đánh giá sản phẩm (chỉ khi đơn COMPLETED) | 2 | Người 4 |
| FR-16 | Email xác nhận đơn, nhắc đơn SHIPPING quá hạn | 2 | Người 4 |
| FR-17 | Dashboard doanh thu, top sản phẩm | 2 | Người 4 (+Người 2) |
| FR-18 | Gợi ý sản phẩm mua kèm (co-purchase) | 2 | Người 4 |
| FR-19 | Cho thuê sản phẩm giới hạn, kiểm tra trùng lịch, cọc | 3 | Người 4 (+Người 1) |

Use case chi tiết (pre/post-condition, exception flow) cho từng FR: xem tài liệu SRS gốc
của nhóm (Tuần 1-2) hoặc bổ sung trực tiếp vào file này khi làm đến FR đó.
