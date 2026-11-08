package vn.campvivo.article.admin;

import org.springframework.data.domain.Sort;
import vn.campvivo.article.ArticleCategory;

/** Tham số GET /api/admin/articles. Giá trị enum viết hoa trên query (sai → 400). */
public record AdminArticleQuery(String q, ArticleCategory category, Boolean isPublished, SortOption sort, int page, int limit) {

    public enum SortOption {
        UPDATED_DESC(Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))),
        CREATED_DESC(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
        TITLE_ASC(Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id")));

        private final Sort sort;

        SortOption(Sort sort) {
            this.sort = sort;
        }

        public Sort toSort() {
            return sort;
        }
    }
}
