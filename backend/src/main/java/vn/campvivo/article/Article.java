package vn.campvivo.article;

import jakarta.persistence.*;
import vn.campvivo.user.User;

import java.time.OffsetDateTime;

/**
 * Bài viết — FR-21, database.md mục 3.21. Nội dung là HTML đã làm sạch.
 * {@code publishedAt} gán ở lần xuất bản đầu tiên và giữ nguyên khi gỡ xuống rồi đăng lại.
 */
@Entity
@Table(name = "articles")
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tác giả: admin tạo bài. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true)
    private String slug;

    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArticleCategory category;

    @Column(name = "is_published", nullable = false)
    private boolean published;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Article() {
    }

    public Article(User author, OffsetDateTime now) {
        this.author = author;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateDetails(String title, String slug, String summary, String content, String thumbnailUrl,
                              ArticleCategory category, OffsetDateTime now) {
        this.title = title;
        this.slug = slug;
        this.summary = summary;
        this.content = content;
        this.thumbnailUrl = thumbnailUrl;
        this.category = category;
        this.updatedAt = now;
    }

    /** Xuất bản / gỡ xuống. Lần xuất bản đầu tiên gán ngày đăng. */
    public void setPublished(boolean published, OffsetDateTime now) {
        if (published && publishedAt == null) {
            publishedAt = now;
        }
        this.published = published;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public User getAuthor() { return author; }
    public String getTitle() { return title; }
    public String getSlug() { return slug; }
    public String getSummary() { return summary; }
    public String getContent() { return content; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public ArticleCategory getCategory() { return category; }
    public boolean isPublished() { return published; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
