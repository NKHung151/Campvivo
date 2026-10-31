package vn.campvivo.payment;

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
import java.util.Map;

/**
 * Thanh toán — database.md mục 3.12: 1 bản ghi / đơn, tạo cùng đơn ở PENDING. Chỉ đổi khi đã khoá dòng `orders`
 * của đơn đó (PaymentResultService / OrderStateService), nên không cần khoá riêng.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    /** Mã giao dịch của cổng (vnp_TransactionNo); null với COD. */
    @Column(name = "transaction_id", unique = true)
    private String transactionId;

    /** Tham số cổng gửi về (đã bỏ chữ ký). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> payload;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Payment() {
        // JPA
    }

    public Payment(Long orderId, PaymentMethod method, BigDecimal amount, OffsetDateTime now) {
        this.orderId = orderId;
        this.method = method;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void markSuccess(String transactionId, Map<String, String> payload, OffsetDateTime now) {
        this.status = PaymentStatus.SUCCESS;
        this.transactionId = transactionId;
        this.payload = payload;
        this.paidAt = now;
        this.updatedAt = now;
    }

    void markFailed(Map<String, String> payload, OffsetDateTime now) {
        this.status = PaymentStatus.FAILED;
        this.payload = payload;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public PaymentMethod getMethod() { return method; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getTransactionId() { return transactionId; }
    public Map<String, String> getPayload() { return payload; }
    public OffsetDateTime getPaidAt() { return paidAt; }
}
