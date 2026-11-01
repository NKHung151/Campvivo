package vn.campvivo.promotion;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * Mã giảm giá trên tổng tiền hàng của đơn (FR-16, BR-5; database.md mục 3.14). `code` luôn IN HOA.
 * `used_count` chỉ đổi qua {@link #consume}/{@link #release} trong transaction đã khoá dòng (VoucherService).
 */
@Entity
@Table(name = "vouchers")
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 30)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 0)
    private BigDecimal discountValue;

    /** Trần giảm cho PERCENTAGE; null = không giới hạn. */
    @Column(name = "max_discount", precision = 19, scale = 0)
    private BigDecimal maxDiscount;

    @Column(name = "min_order_value", nullable = false, precision = 19, scale = 0)
    private BigDecimal minOrderValue = BigDecimal.ZERO;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "start_date", nullable = false)
    private OffsetDateTime startDate;

    @Column(name = "expiry_date", nullable = false)
    private OffsetDateTime expiryDate;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** Hiện trong danh sách mã ở trang thanh toán và PromoBar (D-29). */
    @Column(name = "is_public", nullable = false)
    private boolean publicVisible = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Voucher() {
    }

    public Voucher(String code, OffsetDateTime now) {
        this.code = normalizeCode(code);
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Mã so khớp không phân biệt hoa thường: lưu và tìm theo dạng IN HOA, bỏ khoảng trắng hai đầu. */
    public static String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    public void update(String code, DiscountType discountType, BigDecimal discountValue, BigDecimal maxDiscount,
                       BigDecimal minOrderValue, int quantity, OffsetDateTime startDate, OffsetDateTime expiryDate,
                       boolean active, boolean publicVisible, OffsetDateTime now) {
        this.code = normalizeCode(code);
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscount = discountType == DiscountType.PERCENTAGE ? maxDiscount : null;
        this.minOrderValue = minOrderValue;
        this.quantity = quantity;
        this.startDate = startDate;
        this.expiryDate = expiryDate;
        this.active = active;
        this.publicVisible = publicVisible;
        this.updatedAt = now;
    }

    /** Tạo đơn dùng mã: +1 lượt. Điều kiện BR-5 đã kiểm ở VoucherRules. */
    public void consume() {
        if (usedCount >= quantity) {
            throw new IllegalStateException("Voucher đã hết lượt: " + code);
        }
        usedCount++;
    }

    /** Đơn CANCELLED / PAYMENT_FAILED: trả 1 lượt. */
    public void release() {
        usedCount = Math.max(0, usedCount - 1);
    }

    public int getRemaining() {
        return Math.max(0, quantity - usedCount);
    }

    /** Trạng thái hiển thị cho admin, tính tại thời điểm `now` (không lưu DB). */
    public VoucherStatus statusAt(OffsetDateTime now) {
        if (!active) {
            return VoucherStatus.INACTIVE;
        }
        if (now.isBefore(startDate)) {
            return VoucherStatus.UPCOMING;
        }
        if (!now.isBefore(expiryDate)) {
            return VoucherStatus.EXPIRED;
        }
        return usedCount >= quantity ? VoucherStatus.EXHAUSTED : VoucherStatus.ACTIVE;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public DiscountType getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public BigDecimal getMaxDiscount() { return maxDiscount; }
    public BigDecimal getMinOrderValue() { return minOrderValue; }
    public int getQuantity() { return quantity; }
    public int getUsedCount() { return usedCount; }
    public OffsetDateTime getStartDate() { return startDate; }
    public OffsetDateTime getExpiryDate() { return expiryDate; }
    public boolean isActive() { return active; }
    public boolean isPublicVisible() { return publicVisible; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
