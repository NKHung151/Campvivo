package vn.campvivo.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_code", nullable = false, unique = true)
    private String orderCode;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "final_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "shipping_address", nullable = false, columnDefinition = "TEXT")
    private String shippingAddress;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Column(name = "promotion_code", length = 50)
    private String promotionCode;

    @Column(name = "tracking_code", length = 100)
    private String trackingCode;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    protected Order() {
    }

    public Order(Long userId, String orderCode, String shippingAddress, String paymentMethod) {
        this.userId = userId;
        this.orderCode = orderCode;
        this.shippingAddress = shippingAddress;
        this.paymentMethod = paymentMethod;
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    /**
     * Chuyển trạng thái. PHẢI gọi qua OrderService để Service ghi OrderStatusHistory (BR-7)
     * và gọi InventoryService.commit/release tương ứng — không gọi setStatus trực tiếp từ
     * Controller hay module khác.
     */
    void transitionTo(OrderStatus next) {
        this.status = next;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getOrderCode() { return orderCode; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getFinalAmount() { return finalAmount; }
    public OrderStatus getStatus() { return status; }
    public List<OrderItem> getItems() { return items; }

    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public void setFinalAmount(BigDecimal finalAmount) { this.finalAmount = finalAmount; }
    public void setPromotionCode(String promotionCode) { this.promotionCode = promotionCode; }
    public void setTrackingCode(String trackingCode) { this.trackingCode = trackingCode; }
}
