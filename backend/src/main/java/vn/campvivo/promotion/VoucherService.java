package vn.campvivo.promotion;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.promotion.dto.AvailableVoucherResponse;
import vn.campvivo.promotion.dto.PublicVoucherResponse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Voucher cho đơn hàng (BR-5). {@link #quote} chỉ tính thử (xem trước, ước tính phí ship); {@link #consume} khoá dòng
 * voucher, kiểm tra lại toàn bộ điều kiện rồi trừ lượt (gọi khi tạo đơn, SAU khi khoá products/combos — database.md mục 6);
 * {@link #release} trả lượt khi đơn CANCELLED/PAYMENT_FAILED (OrderStateService).
 */
@Service
public class VoucherService {

    /** Số mã tối đa trên PromoBar (`GET /vouchers/public`). */
    private static final int PUBLIC_LIMIT = 5;

    private final VoucherRepository voucherRepository;
    private final EntityManager em;
    private final Clock clock;

    public VoucherService(VoucherRepository voucherRepository, EntityManager em, Clock clock) {
        this.voucherRepository = voucherRepository;
        this.em = em;
        this.clock = clock;
    }

    /** Kết quả áp mã cho một đơn. Không nhập mã → {@link #NONE}. `code` là mã đã chuẩn hoá (IN HOA) để lưu snapshot. */
    public record Applied(Long voucherId, String code, DiscountType discountType, BigDecimal discountValue,
                          BigDecimal discount) {
        public static final Applied NONE = new Applied(null, null, null, null, BigDecimal.ZERO);
    }

    /** Tính thử mức giảm, không trừ lượt. Vi phạm BR-5 → BusinessException. */
    @Transactional(readOnly = true)
    public Applied quote(String code, BigDecimal subtotal, Long userId) {
        String normalized = Voucher.normalizeCode(code);
        if (normalized == null || normalized.isEmpty()) {
            return Applied.NONE;
        }
        Voucher v = voucherRepository.findByCode(normalized).orElse(null);
        return applied(v, VoucherRules.check(v, subtotal, now(), () -> voucherRepository.existsActiveUsage(v.getId(), userId)));
    }

    /** Tạo đơn: khoá voucher (FOR UPDATE), kiểm tra lại BR-5 trên dữ liệu đã khoá, `used_count + 1`. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Applied consume(String code, BigDecimal subtotal, Long userId) {
        String normalized = Voucher.normalizeCode(code);
        if (normalized == null || normalized.isEmpty()) {
            return Applied.NONE;
        }
        Voucher v = voucherRepository.findByCodeForUpdate(normalized).orElse(null);
        if (v != null) {
            // Entity đã nằm trong persistence context (quote lúc ước tính phí ship) KHÔNG được query khoá cập nhật lại —
            // đọc lại sau khi giữ khoá để kiểm used_count mới nhất (giống InventoryService.lock)
            em.refresh(v);
        }
        // "Đã dùng" kiểm SAU khi có khoá: đơn song song của cùng khách đã commit trước đó sẽ nhìn thấy được
        BigDecimal discount = VoucherRules.check(v, subtotal, now(),
                () -> voucherRepository.existsActiveUsage(v.getId(), userId));
        v.consume();
        return applied(v, discount);
    }

    /** Đơn CANCELLED / PAYMENT_FAILED: trả 1 lượt (cùng transaction đổi trạng thái). Đơn không dùng mã → bỏ qua. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Long voucherId) {
        if (voucherId == null) {
            return;
        }
        voucherRepository.findByIdForUpdate(voucherId).ifPresent(v -> {
            em.refresh(v);
            v.release();
        });
    }

    /**
     * Danh sách mã công khai cho popup trang thanh toán (D-29), tính trên `subtotal` của giỏ hiện tại.
     * Mã dùng được trước (giảm nhiều trước), rồi mã chưa dùng được; mã dùng được giảm nhiều nhất có `best`.
     */
    @Transactional(readOnly = true)
    public List<AvailableVoucherResponse> available(Long userId, BigDecimal subtotal) {
        OffsetDateTime now = now();
        List<AvailableVoucherResponse> usable = new ArrayList<>();
        List<AvailableVoucherResponse> blocked = new ArrayList<>();
        for (Voucher v : voucherRepository.findPublicAvailable(now)) {
            ErrorCode reason = VoucherRules.violation(v, subtotal, now,
                    () -> voucherRepository.existsActiveUsage(v.getId(), userId));
            if (reason == null) {
                usable.add(AvailableVoucherResponse.usable(v, VoucherRules.discount(v, subtotal)));
            } else {
                BigDecimal missing = reason == ErrorCode.VOUCHER_MIN_ORDER_NOT_MET
                        ? v.getMinOrderValue().subtract(subtotal) : null;
                blocked.add(AvailableVoucherResponse.blocked(v, reason.name(), missing));
            }
        }
        usable.sort(Comparator.comparing(AvailableVoucherResponse::estimatedDiscount).reversed());
        blocked.sort(Comparator.comparing(r -> r.missingAmount() == null ? BigDecimal.ZERO : r.missingAmount()));
        List<AvailableVoucherResponse> out = new ArrayList<>(usable.size() + blocked.size());
        for (int i = 0; i < usable.size(); i++) {
            out.add(i == 0 ? usable.get(i).asBest() : usable.get(i));
        }
        out.addAll(blocked);
        return out;
    }

    /** Mã công khai đang có cho PromoBar (D-29), sắp hết hạn trước. */
    @Transactional(readOnly = true)
    public List<PublicVoucherResponse> publicList() {
        return voucherRepository.findPublicAvailable(now()).stream()
                .limit(PUBLIC_LIMIT)
                .map(PublicVoucherResponse::from)
                .toList();
    }

    private static Applied applied(Voucher v, BigDecimal discount) {
        if (v == null) {
            throw new BusinessException(ErrorCode.INVALID_VOUCHER);
        }
        return new Applied(v.getId(), v.getCode(), v.getDiscountType(), v.getDiscountValue(), discount);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
