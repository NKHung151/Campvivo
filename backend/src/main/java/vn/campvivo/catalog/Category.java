package vn.campvivo.catalog;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Danh mục đa cấp — dùng cột `path` (vd. "/1/5/12/") thay vì recursive CTE (BR-13,
 * docs/02-ky-thuat/database.md mục 3.2). Tạo/sửa/xoá danh mục: AdminCategoryService (N2-18).
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    /** Dạng "/1/5/12/" (gồm chính nó) — sinh ở AdminCategoryService sau khi có id, không set tay từ API. */
    @Column(nullable = false)
    private String path;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Category() {
    }

    /** Path tạm "" — gán path thật bằng {@link #assignPath()} sau khi insert có id. */
    public Category(Category parent, String name, String slug, OffsetDateTime now) {
        this.parent = parent;
        this.name = name;
        this.slug = slug;
        this.path = "";
        this.updatedAt = now;
    }

    /** path = path cha + id + "/" (gốc: "/" + id + "/"). */
    public void assignPath() {
        this.path = (parent == null ? "/" : parent.getPath()) + id + "/";
    }

    public void update(String name, String slug, OffsetDateTime now) {
        this.name = name;
        this.slug = slug;
        this.updatedAt = now;
    }

    /** Đổi cha; path của chính nó và cả cây con cập nhật bằng câu UPDATE riêng (CategoryRepository.movePaths). */
    public void moveTo(Category newParent, OffsetDateTime now) {
        this.parent = newParent;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public Category getParent() { return parent; }
    public Long getParentId() { return parent == null ? null : parent.getId(); }
    public String getPath() { return path; }
}
