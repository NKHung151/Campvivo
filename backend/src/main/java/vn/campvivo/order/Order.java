package vn.campvivo.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import vn.campvivo.inventory.StockLine;
import vn.campvivo.payment.PaymentMethod;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Đơn hàng — database.md mục 3.9 (+ cột GHN, D-27/D-28). Tiền chốt lúc tạo (BR-4), không sửa sau đó.
 * Trạng thái chỉ đổi qua OrderStateService (khoá dòng, máy trạng thái, tồn kho, lịch sử).
 */
@Entity
@Table(name = "orders")
public class Order {

    /** Người nhận + địa chỉ giao (snapshot) kèm mã GHN để tạo vận đơn. */
    public record Recipient(String name, String phone, String address, int districtId, String wardCode) {}

    /** Tiền đã chốt (BR-4): final = subtotal − discount + shippingFee. */
    public record Amounts(BigDecimal subtotal, BigDecimal discountAmount, BigDecimal shippingFee, BigDecimal finalAmount) {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", nullable = false, unique = true)
    private String orderCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "voucher_id")
    private Long voucherId;

    @Column(name = "voucher_code")
    private String voucherCode;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal discountAmount;

    @Column(name = "shipping_fee", nullable = false, precision = 19, scale = 0)
    private BigDecimal shippingFee;

    @Column(name = "final_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal finalAmount;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false)
    private String recipientPhone;

    @Column(name = "shipping_address", nullable = false)
    private String shippingAddress;

    @Column(name = "shipping_district_id", nullable = false)
    private int shippingDistrictId;

    @Column(name = "shipping_ward_code", nullable = false)
    private String shippingWardCode;

    /** Mã vận đơn GHN; null = chưa tạo. */
    @Column(name = "shipping_order_code")
    private String shippingOrderCode;

    /** Lỗi lần tạo vận đơn gần nhất (để nhân viên biết và tạo lại); null khi đã tạo được. */
    @Column(name = "shipping_order_error")
    private String shippingOrderError;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    private String note;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // BatchSize: danh sách đơn nạp dòng hàng của cả trang bằng vài câu SQL thay vì mỗi đơn một câu
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @BatchSize(size = 100)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    @BatchSize(size = 100)
    private List<OrderStatusHistory> history = new ArrayList<>();

    protected Order() {
        // JPA
    }

    /** Đơn mới ở PENDING, ghi luôn dòng lịch sử đầu tiên (BR-7). */
    public Order(String orderCode, Long userId, String voucherCode, Amounts amounts, Recipient recipient,
                 PaymentMethod paymentMethod, String note, OffsetDateTime now) {
        this.orderCode = orderCode;
        this.userId = userId;
        this.voucherCode = voucherCode;
        this.subtotal = amounts.subtotal();
        this.discountAmount = amounts.discountAmount();
        this.shippingFee = amounts.shippingFee();
        this.finalAmount = amounts.finalAmount();
        this.recipientName = recipient.name();
        this.recipientPhone = recipient.phone();
        this.shippingAddress = recipient.address();
        this.shippingDistrictId = recipient.districtId();
        this.shippingWardCode = recipient.wardCode();
        this.paymentMethod = paymentMethod;
        this.note = note;
        this.status = OrderStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
        this.history.add(new OrderStatusHistory(this, null, OrderStatus.PENDING, userId, null, now));
    }

    public void addItem(Long productId, Long comboId, String name, String imageUrl,
                        BigDecimal originalPrice, BigDecimal unitPrice, int quantity) {
        items.add(new OrderItem(this, productId, comboId, name, imageUrl, originalPrice, unitPrice, quantity));
    }

    /** Chỉ gọi từ OrderStateService (đã kiểm máy trạng thái, đã khoá dòng). */
    void moveTo(OrderStatus to, Long changedBy, String note, OffsetDateTime now) {
        this.history.add(new OrderStatusHistory(this, this.status, to, changedBy, note, now));
        this.status = to;
        this.updatedAt = now;
        if (to == OrderStatus.COMPLETED) {
            this.completedAt = now;
        }
    }

    void recordShipment(String shippingOrderCode, OffsetDateTime now) {
        this.shippingOrderCode = shippingOrderCode;
        this.shippingOrderError = null;
        this.updatedAt = now;
    }

    void recordShipmentError(String error, OffsetDateTime now) {
        this.shippingOrderError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        this.updatedAt = now;
    }

    public List<StockLine> stockLines() {
        return items.stream().map(OrderItem::toStockLine).toList();
    }

    public int totalQuantity() {
        return items.stream().mapToInt(OrderItem::getQuantity).sum();
    }

    public Long getId() { return id; }
    public String getOrderCode() { return orderCode; }
    public Long getUserId() { return userId; }
    public Long getVoucherId() { return voucherId; }
    public String getVoucherCode() { return voucherCode; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getShippingFee() { return shippingFee; }
    public BigDecimal getFinalAmount() { return finalAmount; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getShippingAddress() { return shippingAddress; }
    public int getShippingDistrictId() { return shippingDistrictId; }
    public String getShippingWardCode() { return shippingWardCode; }
    public String getShippingOrderCode() { return shippingOrderCode; }
    public String getShippingOrderError() { return shippingOrderError; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public OrderStatus getStatus() { return status; }
    public String getNote() { return note; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<OrderItem> getItems() { return items; }
    public List<OrderStatusHistory> getHistory() { return history; }
}
