package vn.campvivo.promotion;

import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Điều kiện dùng voucher theo BR-5, thuần Java (không DB). Thứ tự lỗi cố định: không tồn tại/tắt → INVALID_VOUCHER;
 * ngoài thời hạn → VOUCHER_EXPIRED; chưa đạt tối thiểu → VOUCHER_MIN_ORDER_NOT_MET; hết lượt → VOUCHER_EXHAUSTED;
 * khách đã dùng → VOUCHER_ALREADY_USED. `alreadyUsed` chỉ được gọi khi mọi điều kiện trước đã đạt (tránh query thừa).
 */
public final class VoucherRules {

    private VoucherRules() {
    }

    /** Điều kiện đầu tiên bị vi phạm, hoặc null nếu dùng được. Hiệu lực: `start_date ≤ now < expiry_date`. */
    public static ErrorCode violation(Voucher v, BigDecimal subtotal, OffsetDateTime now, BooleanSupplier alreadyUsed) {
        if (v == null || !v.isActive()) {
            return ErrorCode.INVALID_VOUCHER;
        }
        if (now.isBefore(v.getStartDate()) || !now.isBefore(v.getExpiryDate())) {
            return ErrorCode.VOUCHER_EXPIRED;
        }
        if (subtotal.compareTo(v.getMinOrderValue()) < 0) {
            return ErrorCode.VOUCHER_MIN_ORDER_NOT_MET;
        }
        if (v.getUsedCount() >= v.getQuantity()) {
            return ErrorCode.VOUCHER_EXHAUSTED;
        }
        if (alreadyUsed.getAsBoolean()) {
            return ErrorCode.VOUCHER_ALREADY_USED;
        }
        return null;
    }

    /** Kiểm tra BR-5 rồi trả mức giảm; vi phạm → BusinessException (thiếu tối thiểu kèm `details.minOrderValue`). */
    public static BigDecimal check(Voucher v, BigDecimal subtotal, OffsetDateTime now, BooleanSupplier alreadyUsed) {
        ErrorCode error = violation(v, subtotal, now, alreadyUsed);
        if (error == ErrorCode.VOUCHER_MIN_ORDER_NOT_MET) {
            throw new BusinessException(error, "Đơn hàng cần tối thiểu " + formatVnd(v.getMinOrderValue())
                    + " để dùng mã " + v.getCode() + ".", Map.of("minOrderValue", v.getMinOrderValue()));
        }
        if (error != null) {
            throw new BusinessException(error);
        }
        return discount(v, subtotal);
    }

    /** Mức giảm cho `subtotal` (BR-5), không kiểm tra điều kiện. */
    public static BigDecimal discount(Voucher v, BigDecimal subtotal) {
        return Discounts.voucherDiscount(subtotal, v.getDiscountType(), v.getDiscountValue(), v.getMaxDiscount());
    }

    private static String formatVnd(BigDecimal amount) {
        return String.format("%,d", amount.longValueExact()).replace(',', '.') + "đ";
    }
}
