package vn.campvivo.catalog;

import jakarta.persistence.*;

/**
 * Danh mục đa cấp — dùng cột `path` (vd. "/1/5/12/") thay vì recursive CTE, như quy định
 * trong docs/02-ky-thuat/database.md.
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

    /** Dạng "/1/5/12/" — tự sinh ở CategoryService khi tạo, không set tay. */
    @Column(nullable = false)
    private String path;

    protected Category() {
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public Category getParent() { return parent; }
    public String getPath() { return path; }
}
