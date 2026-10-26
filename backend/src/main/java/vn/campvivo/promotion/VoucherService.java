package vn.campvivo.promotion;

import org.springframework.stereotype.Service;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;

import java.math.BigDecimal;

/**
 * Điểm cắm voucher cho đơn hàng (BR-5). Bảng `vouchers` (F-07) chưa có nên mọi mã đều "không tồn tại" →
 * INVALID_VOUCHER. Khi làm FR-16: kiểm tra đủ điều kiện BR-5 ở {@link #discountFor}, và thêm consume/release
 * (khoá dòng voucher, used_count ± 1) gọi từ OrderService / OrderStateService.
 */
@Service
public class VoucherService {

    /** Số tiền giảm cho `subtotal` (đã HALF_UP, ≤ subtotal). Mã rỗng → 0. */
    public BigDecimal discountFor(String code, BigDecimal subtotal, Long userId) {
        if (code == null || code.isBlank()) {
            return BigDecimal.ZERO;
        }
        throw new BusinessException(ErrorCode.INVALID_VOUCHER);
    }
}
