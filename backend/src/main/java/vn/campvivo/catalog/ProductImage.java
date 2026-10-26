package vn.campvivo.catalog;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Ảnh của sản phẩm — bảng `images` (docs/02-ky-thuat/database.md mục 3.4). Bảng dùng chung cho combo
 * (`combo_id`); entity này chỉ ánh xạ phía sản phẩm, ảnh combo do module combo quản lý.
 */
@Entity
@Table(name = "images")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "image_url", nullable = false, unique = true, length = 500)
    private String imageUrl;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ProductImage() {
        // JPA
    }

    ProductImage(Product product, String imageUrl, boolean primary, int displayOrder, OffsetDateTime now) {
        this.product = product;
        this.imageUrl = imageUrl;
        this.primary = primary;
        this.displayOrder = displayOrder;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public String getImageUrl() { return imageUrl; }
    public boolean isPrimary() { return primary; }
    public int getDisplayOrder() { return displayOrder; }
}
