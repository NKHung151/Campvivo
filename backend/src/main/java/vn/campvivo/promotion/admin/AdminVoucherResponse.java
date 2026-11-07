package vn.campvivo.promotion.admin;

import vn.campvivo.promotion.DiscountType;
import vn.campvivo.promotion.Voucher;
import vn.campvivo.promotion.VoucherStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Kiểu `AdminVoucher` (api.md mục 4.10). `status` tính tại thời điểm trả về. */
public record AdminVoucherResponse(
        Long id,
        String code,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscount,
        BigDecimal minOrderValue,
        int quantity,
        int usedCount,
        OffsetDateTime startDate,
        OffsetDateTime expiryDate,
        boolean isActive,
        boolean isPublic,
        VoucherStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AdminVoucherResponse from(Voucher v, OffsetDateTime now) {
        return new AdminVoucherResponse(v.getId(), v.getCode(), v.getDiscountType(), v.getDiscountValue(),
                v.getMaxDiscount(), v.getMinOrderValue(), v.getQuantity(), v.getUsedCount(), v.getStartDate(),
                v.getExpiryDate(), v.isActive(), v.isPublicVisible(), v.statusAt(now), v.getCreatedAt(), v.getUpdatedAt());
    }
}
