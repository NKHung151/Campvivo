package vn.campvivo.promotion;

import jakarta.persistence.*;
import vn.campvivo.catalog.Product;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Chiến dịch giảm giá theo sản phẩm, có thời hạn (FR-17, BR-6; database.md mục 3.15–3.16). Không áp cho combo.
 * Giá sau giảm KHÔNG tính ở đây mà qua `Product.salePrice` (D-30) — {@link #priceFor} chỉ để admin xem trước.
 */
@Entity
@Table(name = "discount_campaigns")
public class DiscountCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 280)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "banner_url", length = 500)
    private String bannerUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 30)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 0)
    private BigDecimal discountValue;

    @Column(name = "start_date", nullable = false)
    private OffsetDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private OffsetDateTime endDate;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToMany
    @JoinTable(name = "discount_campaign_products",
            joinColumns = @JoinColumn(name = "campaign_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id"))
    @OrderBy("id ASC")
    private Set<Product> products = new LinkedHashSet<>();

    protected DiscountCampaign() {
    }

    public DiscountCampaign(OffsetDateTime now) {
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String slug, String description, String bannerUrl, DiscountType discountType,
                       BigDecimal discountValue, OffsetDateTime startDate, OffsetDateTime endDate, boolean active,
                       OffsetDateTime now) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.bannerUrl = bannerUrl;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.startDate = startDate;
        this.endDate = endDate;
        this.active = active;
        this.updatedAt = now;
    }

    public void replaceProducts(Set<Product> products) {
        this.products.clear();
        this.products.addAll(products);
    }

    /** Giá của sản phẩm nếu chỉ áp chiến dịch này — cùng công thức với `Product.salePrice` (BR-6). */
    public BigDecimal priceFor(BigDecimal price) {
        return Discounts.campaignPrice(price, discountType, discountValue);
    }

    /** Trạng thái hiển thị cho admin, tính tại thời điểm `now` (không lưu DB). */
    public CampaignStatus statusAt(OffsetDateTime now) {
        if (!active) {
            return CampaignStatus.INACTIVE;
        }
        if (now.isBefore(startDate)) {
            return CampaignStatus.UPCOMING;
        }
        return now.isBefore(endDate) ? CampaignStatus.RUNNING : CampaignStatus.ENDED;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getBannerUrl() { return bannerUrl; }
    public DiscountType getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public OffsetDateTime getStartDate() { return startDate; }
    public OffsetDateTime getEndDate() { return endDate; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public Set<Product> getProducts() { return products; }
}
