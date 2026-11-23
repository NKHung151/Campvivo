package vn.campvivo.order.returns;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Yêu cầu trả hàng — database.md mục 3.13, BR-9 (D-07, D-41): trả nguyên đơn, mỗi đơn tối đa 1 yêu cầu.
 * Chỉ giữ id đơn (không nạp entity); đổi trạng thái qua {@link ReturnService} sau khi đã khoá đơn.
 */
@Entity
@Table(name = "return_requests")
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReturnReason reason;

    @Column(length = 2000)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "image_urls", nullable = false, columnDefinition = "jsonb")
    private List<String> imageUrls = new ArrayList<>();

    /** Tạm tính − giảm giá của đơn lúc gửi yêu cầu (phí vận chuyển không hoàn). */
    @Column(name = "refund_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReturnStatus status;

    @Column(name = "processed_by")
    private Long processedBy;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(length = 1000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ReturnRequest() {
        // JPA
    }

    ReturnRequest(Long orderId, ReturnReason reason, String description, List<String> imageUrls,
                  BigDecimal refundAmount, OffsetDateTime now) {
        this.orderId = orderId;
        this.reason = reason;
        this.description = description;
        this.imageUrls = new ArrayList<>(imageUrls);
        this.refundAmount = refundAmount;
        this.status = ReturnStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** PENDING → APPROVED / REJECTED (bên gọi đã kiểm trạng thái hiện tại sau khi khoá). */
    void process(ReturnStatus to, Long staffId, String note, OffsetDateTime now) {
        this.status = to;
        this.processedBy = staffId;
        this.processedAt = now;
        this.note = note;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public ReturnReason getReason() { return reason; }
    public String getDescription() { return description; }
    public List<String> getImageUrls() { return imageUrls; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public ReturnStatus getStatus() { return status; }
    public Long getProcessedBy() { return processedBy; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
    public String getNote() { return note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
