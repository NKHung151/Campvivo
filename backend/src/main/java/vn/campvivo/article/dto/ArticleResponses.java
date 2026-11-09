package vn.campvivo.article.dto;

import vn.campvivo.article.Article;
import vn.campvivo.article.ArticleCategory;

import java.time.OffsetDateTime;

/** Response bài viết public — api.md mục 4.3 (`ArticleSummary`, `ArticleDetail`); khớp frontend/src/types/api/article.ts. */
public final class ArticleResponses {

    private ArticleResponses() {
    }

    public record Summary(Long id, String title, String slug, String summary, String thumbnailUrl,
                          ArticleCategory category, OffsetDateTime publishedAt) {
        public static Summary from(Article a) {
            return new Summary(a.getId(), a.getTitle(), a.getSlug(), a.getSummary(), a.getThumbnailUrl(), a.getCategory(),
                    a.getPublishedAt());
        }
    }

    /** Gọi trong transaction hoặc khi đã nạp tác giả. `isFavorite` sẽ do module yêu thích bổ sung. */
    public record Detail(Long id, String title, String slug, String summary, String thumbnailUrl,
                         ArticleCategory category, OffsetDateTime publishedAt, String content, String authorName) {
        public static Detail from(Article a) {
            return new Detail(a.getId(), a.getTitle(), a.getSlug(), a.getSummary(), a.getThumbnailUrl(), a.getCategory(),
                    a.getPublishedAt(), a.getContent(), a.getAuthor().getFullName());
        }
    }
}
