package vn.campvivo.promotion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Voucher công khai vừa hiệu lực, được {@link PromotionAnnouncementJob} công bố đúng một lần (D-45). */
public record VoucherAnnouncedEvent(long voucherId, String code, DiscountType discountType, BigDecimal discountValue,
                                    BigDecimal maxDiscount, BigDecimal minOrderValue, OffsetDateTime expiryDate) {

    static VoucherAnnouncedEvent of(Voucher v) {
        return new VoucherAnnouncedEvent(v.getId(), v.getCode(), v.getDiscountType(), v.getDiscountValue(),
                v.getMaxDiscount(), v.getMinOrderValue(), v.getExpiryDate());
    }
}
