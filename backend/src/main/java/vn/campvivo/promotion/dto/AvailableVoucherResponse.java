package vn.campvivo.promotion.dto;

import vn.campvivo.promotion.DiscountType;
import vn.campvivo.promotion.Voucher;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Kiểu `AvailableVoucher` (api.md mục 4.5) — một mã trong danh sách ở popup voucher trang `/cart` (D-29). */
public record AvailableVoucherResponse(
        String code,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscount,
        BigDecimal minOrderValue,
        OffsetDateTime expiryDate,
        int remaining,
        boolean usable,
        String reason,
        BigDecimal missingAmount,
        BigDecimal estimatedDiscount,
        boolean best
) {
    public static AvailableVoucherResponse usable(Voucher v, BigDecimal discount) {
        return new AvailableVoucherResponse(v.getCode(), v.getDiscountType(), v.getDiscountValue(), v.getMaxDiscount(),
                v.getMinOrderValue(), v.getExpiryDate(), v.getRemaining(), true, null, null, discount, false);
    }

    /** `reason`: VOUCHER_MIN_ORDER_NOT_MET (kèm `missingAmount`) hoặc VOUCHER_ALREADY_USED. */
    public static AvailableVoucherResponse blocked(Voucher v, String reason, BigDecimal missingAmount) {
        return new AvailableVoucherResponse(v.getCode(), v.getDiscountType(), v.getDiscountValue(), v.getMaxDiscount(),
                v.getMinOrderValue(), v.getExpiryDate(), v.getRemaining(), false, reason, missingAmount,
                BigDecimal.ZERO, false);
    }

    public AvailableVoucherResponse asBest() {
        return new AvailableVoucherResponse(code, discountType, discountValue, maxDiscount, minOrderValue, expiryDate,
                remaining, usable, reason, missingAmount, estimatedDiscount, true);
    }
}
