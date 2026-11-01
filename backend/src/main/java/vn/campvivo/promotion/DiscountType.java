package vn.campvivo.promotion;

/** Loại giảm giá của voucher và chiến dịch (database.md mục 3.14, 3.15). */
public enum DiscountType {
    /** Giảm theo %, `discount_value` 1–100. */
    PERCENTAGE,
    /** Giảm một số tiền cố định, `discount_value` > 0. */
    FIXED_AMOUNT
}
