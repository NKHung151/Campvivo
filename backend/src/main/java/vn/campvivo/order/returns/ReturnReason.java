package vn.campvivo.order.returns;

/** Lý do trả hàng khách chọn (D-41) — cùng danh sách với `RETURN_REASONS` ở frontend. */
public enum ReturnReason {
    MISSING_ITEMS("Thiếu hàng"),
    WRONG_ITEM("Gửi sai hàng"),
    DAMAGED("Hàng bể vỡ"),
    DEFECTIVE("Hàng lỗi, không hoạt động"),
    EXPIRED("Hàng hết hạn sử dụng"),
    NOT_AS_DESCRIBED("Khác với mô tả"),
    USED("Hàng đã qua sử dụng"),
    COUNTERFEIT("Hàng giả, nhái"),
    CHANGED_MIND("Đổi ý (còn nguyên tem, nhãn mác, bao bì)");

    private final String label;

    ReturnReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
