package vn.campvivo.article.admin;

import vn.campvivo.article.Article;
import vn.campvivo.article.ArticleCategory;

import java.time.OffsetDateTime;

/** Response admin bài viết — khớp frontend/src/types/api/article.ts (`AdminArticleSummary`, `AdminArticleDetail`). */
public final class AdminArticleResponses {

    private AdminArticleResponses() {
    }

    /** Gọi trong transaction (tác giả nạp lazy). */
    public record Summary(Long id, String title, String slug, String summary, String thumbnailUrl, ArticleCategory category,
                          boolean isPublished, OffsetDateTime publishedAt, String authorName, OffsetDateTime createdAt,
                          OffsetDateTime updatedAt) {
        public static Summary from(Article a) {
            return new Summary(a.getId(), a.getTitle(), a.getSlug(), a.getSummary(), a.getThumbnailUrl(), a.getCategory(),
                    a.isPublished(), a.getPublishedAt(), a.getAuthor().getFullName(), a.getCreatedAt(), a.getUpdatedAt());
        }
    }

    public record Detail(Long id, String title, String slug, String summary, String content, String thumbnailUrl,
                         ArticleCategory category, boolean isPublished, OffsetDateTime publishedAt, String authorName,
                         OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        public static Detail from(Article a) {
            return new Detail(a.getId(), a.getTitle(), a.getSlug(), a.getSummary(), a.getContent(), a.getThumbnailUrl(),
                    a.getCategory(), a.isPublished(), a.getPublishedAt(), a.getAuthor().getFullName(), a.getCreatedAt(),
                    a.getUpdatedAt());
        }
    }
}
