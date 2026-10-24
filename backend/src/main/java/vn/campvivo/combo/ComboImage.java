package vn.campvivo.combo;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Ảnh tự tải lên của combo — bảng `images` dùng chung với sản phẩm (cột `combo_id`, database.md mục 3.4).
 * Ngoài ảnh này, combo còn hiển thị ảnh chính của các sản phẩm thành phần (D-26).
 */
@Entity
@Table(name = "images")
public class ComboImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id")
    private ProductCombo combo;

    @Column(name = "image_url", nullable = false, unique = true, length = 500)
    private String imageUrl;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ComboImage() {
        // JPA
    }

    ComboImage(ProductCombo combo, String imageUrl, boolean primary, int displayOrder, OffsetDateTime now) {
        this.combo = combo;
        this.imageUrl = imageUrl;
        this.primary = primary;
        this.displayOrder = displayOrder;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public ProductCombo getCombo() { return combo; }
    public String getImageUrl() { return imageUrl; }
    public boolean isPrimary() { return primary; }
    public int getDisplayOrder() { return displayOrder; }
}
