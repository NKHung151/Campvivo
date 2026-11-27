package vn.campvivo.favorite;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Một mục yêu thích của khách (FR-20; database.md mục 3.18). Đúng một trong `productId` / `comboId` / `articleId`. Dòng được
 * ghi bằng JDBC (`INSERT … ON CONFLICT DO NOTHING` — {@link FavoriteService}); entity dùng để đọc và xoá.
 */
@Entity
@Table(name = "favorites")
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "combo_id")
    private Long comboId;

    @Column(name = "article_id")
    private Long articleId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Favorite() {
    }

    public FavoriteType getType() {
        return productId != null ? FavoriteType.PRODUCT : comboId != null ? FavoriteType.COMBO : FavoriteType.ARTICLE;
    }

    public Long getItemId() {
        return productId != null ? productId : comboId != null ? comboId : articleId;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getComboId() {
        return comboId;
    }

    public Long getArticleId() {
        return articleId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
