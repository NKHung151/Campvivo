package vn.campvivo.promotion.dto;

import vn.campvivo.promotion.DiscountType;
import vn.campvivo.promotion.Voucher;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Kiểu `PublicVoucher` (api.md mục 4.5) — mã công khai trên PromoBar. */
public record PublicVoucherResponse(
        String code,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscount,
        BigDecimal minOrderValue,
        OffsetDateTime expiryDate
) {
    public static PublicVoucherResponse from(Voucher v) {
        return new PublicVoucherResponse(v.getCode(), v.getDiscountType(), v.getDiscountValue(), v.getMaxDiscount(),
                v.getMinOrderValue(), v.getExpiryDate());
    }
}
