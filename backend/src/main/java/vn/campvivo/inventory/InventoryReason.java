package vn.campvivo.inventory;

/** Lý do thay đổi tồn kho trong `inventory_logs` (database.md mục 3.22). */
public enum InventoryReason {
    /** Nhập hàng (số dương); cũng dùng khi tháo combo trả hàng lẻ về kho. */
    IMPORT,
    /** Xuất hàng / hỏng / mất (số âm); cũng dùng khi đóng gói combo lấy hàng lẻ ra. */
    EXPORT,
    /** Điều chỉnh sau kiểm kê (âm hoặc dương). */
    ADJUST,
    /** Trả hàng hoàn tất (BR-9) — module order ghi, không chọn được khi điều chỉnh tay. */
    RETURN
}
