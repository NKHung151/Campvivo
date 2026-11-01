package vn.campvivo.promotion.admin;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.promotion.DiscountType;
import vn.campvivo.promotion.Voucher;
import vn.campvivo.promotion.VoucherRepository;
import vn.campvivo.promotion.VoucherStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;

import static vn.campvivo.catalog.ProductSpecifications.escapeLike;

/**
 * Quản lý voucher — FR-16. Mã lưu IN HOA, duy nhất. Voucher đã gắn với đơn nào thì không xoá được (`VOUCHER_IN_USE`),
 * chỉ tắt. Sửa không được giảm `quantity` xuống dưới số đã dùng.
 */
@Service
public class AdminVoucherService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final VoucherRepository voucherRepository;
    private final Clock clock;

    public AdminVoucherService(VoucherRepository voucherRepository, Clock clock) {
        this.voucherRepository = voucherRepository;
        this.clock = clock;
    }

    /** Lọc theo mã (chứa chuỗi) và trạng thái tính tại lúc gọi; mới tạo trước. */
    @Transactional(readOnly = true)
    public PageResponse<AdminVoucherResponse> list(String q, VoucherStatus status, int page, int limit) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Specification<Voucher> spec = Specification.<Voucher>where(codeContains(q)).and(hasStatus(status, now));
        return PageResponse.of(voucherRepository.findAll(spec,
                        PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .map(v -> AdminVoucherResponse.from(v, now)));
    }

    @Transactional(readOnly = true)
    public AdminVoucherResponse get(Long id) {
        return AdminVoucherResponse.from(find(id), OffsetDateTime.now(clock));
    }

    @Transactional
    public AdminVoucherResponse create(VoucherRequest req) {
        validate(req);
        String code = Voucher.normalizeCode(req.code());
        if (voucherRepository.existsByCode(code)) {
            throw new BusinessException(ErrorCode.VOUCHER_CODE_ALREADY_EXISTS);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        Voucher v = new Voucher(code, now);
        apply(v, req, now);
        return AdminVoucherResponse.from(voucherRepository.save(v), now);
    }

    @Transactional
    public AdminVoucherResponse update(Long id, VoucherRequest req) {
        validate(req);
        // Khoá dòng: không để đơn đang đặt tăng used_count chen giữa lúc kiểm tra quantity
        Voucher v = voucherRepository.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        String code = Voucher.normalizeCode(req.code());
        if (voucherRepository.existsByCodeAndIdNot(code, id)) {
            throw new BusinessException(ErrorCode.VOUCHER_CODE_ALREADY_EXISTS);
        }
        if (req.quantity() < v.getUsedCount()) {
            throw fieldError("quantity", "Số lượng phát hành không được nhỏ hơn số lượt đã dùng (" + v.getUsedCount() + ").");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        apply(v, req, now);
        return AdminVoucherResponse.from(v, now);
    }

    @Transactional
    public void delete(Long id) {
        Voucher v = find(id);
        if (voucherRepository.hasOrders(id)) {
            throw new BusinessException(ErrorCode.VOUCHER_IN_USE);
        }
        voucherRepository.delete(v);
    }

    private static void apply(Voucher v, VoucherRequest req, OffsetDateTime now) {
        v.update(req.code(), req.discountType(), req.discountValue(), req.maxDiscount(), req.minOrderValue(),
                req.quantity(), req.startDate(), req.expiryDate(), req.isActive(), req.isPublic(), now);
    }

    /** Ràng buộc chéo giữa các trường (khớp CHECK của V6__promotion.sql, báo lỗi đúng ô). */
    private static void validate(VoucherRequest req) {
        if (req.discountType() == DiscountType.PERCENTAGE && req.discountValue().compareTo(HUNDRED) > 0) {
            throw fieldError("discountValue", "Giảm theo % chỉ từ 1 đến 100.");
        }
        if (req.discountType() == DiscountType.FIXED_AMOUNT && req.maxDiscount() != null) {
            throw fieldError("maxDiscount", "Chỉ voucher giảm theo % mới có mức giảm tối đa.");
        }
        if (!req.expiryDate().isAfter(req.startDate())) {
            throw fieldError("expiryDate", "Ngày hết hạn phải sau ngày bắt đầu.");
        }
    }

    private Voucher find(Long id) {
        return voucherRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private static BusinessException fieldError(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                Map.of("fieldErrors", Map.of(field, message)));
    }

    // --- Specification ---------------------------------------------------------------------------

    private static Specification<Voucher> codeContains(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(Voucher.normalizeCode(q)) + "%";
        return (root, query, cb) -> cb.like(root.get("code"), pattern, '\\');
    }

    /** Cùng nghĩa với {@link Voucher#statusAt}. */
    private static Specification<Voucher> hasStatus(VoucherStatus status, OffsetDateTime now) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> {
            var active = cb.isTrue(root.get("active"));
            var started = cb.lessThanOrEqualTo(root.<OffsetDateTime>get("startDate"), now);
            var notExpired = cb.greaterThan(root.<OffsetDateTime>get("expiryDate"), now);
            var exhausted = cb.greaterThanOrEqualTo(root.<Integer>get("usedCount"), root.<Integer>get("quantity"));
            return switch (status) {
                case INACTIVE -> cb.isFalse(root.get("active"));
                case UPCOMING -> cb.and(active, cb.greaterThan(root.<OffsetDateTime>get("startDate"), now));
                case EXPIRED -> cb.and(active, cb.lessThanOrEqualTo(root.<OffsetDateTime>get("expiryDate"), now));
                case EXHAUSTED -> cb.and(active, started, notExpired, exhausted);
                case ACTIVE -> cb.and(active, started, notExpired, cb.not(exhausted));
            };
        };
    }
}
