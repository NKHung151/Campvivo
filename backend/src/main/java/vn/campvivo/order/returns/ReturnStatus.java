package vn.campvivo.order.returns;

/**
 * Trạng thái yêu cầu trả hàng — database.md mục 5.2 (D-41): PENDING → APPROVED (cộng lại kho, đơn RETURNED) hoặc
 * REJECTED. Duyệt là hoàn tất, không có bước "đã nhận hàng" riêng.
 */
public enum ReturnStatus {
    PENDING,
    APPROVED,
    REJECTED
}
