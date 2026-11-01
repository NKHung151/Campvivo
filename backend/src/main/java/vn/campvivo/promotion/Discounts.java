package vn.campvivo.promotion;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Công thức tiền thuần Java (không DB), làm tròn HALF_UP về đồng.
 * Giá chiến dịch ở đây PHẢI khớp biểu thức SQL `Product.salePrice` (D-30) — chỉ dùng cho xem trước ở admin và test.
 */
public final class Discounts {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Discounts() {
    }

    /** BR-6: PERCENTAGE → price × (100 − value) / 100; FIXED_AMOUNT → max(price − value, 0). */
    public static BigDecimal campaignPrice(BigDecimal price, DiscountType type, BigDecimal value) {
        if (type == DiscountType.PERCENTAGE) {
            return price.multiply(HUNDRED.subtract(value)).divide(HUNDRED, 0, RoundingMode.HALF_UP);
        }
        return price.subtract(value).max(BigDecimal.ZERO);
    }

    /** BR-5: PERCENTAGE → subtotal × value / 100, giới hạn bởi `maxDiscount`; FIXED_AMOUNT → value. Luôn ≤ subtotal. */
    public static BigDecimal voucherDiscount(BigDecimal subtotal, DiscountType type, BigDecimal value, BigDecimal maxDiscount) {
        BigDecimal discount;
        if (type == DiscountType.PERCENTAGE) {
            discount = subtotal.multiply(value).divide(HUNDRED, 0, RoundingMode.HALF_UP);
            if (maxDiscount != null) {
                discount = discount.min(maxDiscount);
            }
        } else {
            discount = value;
        }
        return discount.min(subtotal).max(BigDecimal.ZERO);
    }
}
