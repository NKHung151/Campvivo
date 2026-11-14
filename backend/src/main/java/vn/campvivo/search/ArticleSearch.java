package vn.campvivo.search;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.article.Article;
import vn.campvivo.article.ArticleRepository;
import vn.campvivo.article.dto.ArticleResponses;
import vn.campvivo.common.util.SlugUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Tìm bài viết cho `GET /search` (FR-07): chỉ bài ĐÃ XUẤT BẢN, theo TIÊU ĐỀ. Khớp nếu tiêu đề chứa nguyên cụm từ khoá (có dấu,
 * không phân biệt hoa thường) hoặc MỌI từ của từ khoá (gõ không dấu) nằm trọn trong slug của bài. Xếp theo mức liên quan
 * (tiêu đề bắt đầu bằng từ khoá, rồi tiêu đề chứa từ khoá, rồi còn lại), sau đó mới nhất trước.
 * Không sửa `article` (module của Người 2): chỉ dùng `ArticleRepository` có sẵn (JpaSpecificationExecutor).
 */
@Component
public class ArticleSearch {

    private final ArticleRepository repository;

    public ArticleSearch(ArticleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<ArticleResponses.Summary> search(String q, int page, int limit) {
        String text = escapeLike(q.trim().toLowerCase(Locale.ROOT));
        List<String> words = Arrays.stream(SlugUtil.toSlug(q).split("-")).filter(w -> !w.isEmpty()).toList();

        Specification<Article> spec = (root, query, cb) -> {
            Predicate published = cb.isTrue(root.get("published"));
            Expression<String> lowerTitle = cb.lower(root.get("title"));
            Predicate titleContains = cb.like(lowerTitle, "%" + text + "%", '\\');
            Predicate matches = titleContains;
            if (!words.isEmpty()) {
                var dashedSlug = cb.concat(cb.concat("-", root.<String>get("slug")), "-");
                Predicate allWords = cb.and(words.stream()
                        .map(w -> cb.like(dashedSlug, "%-" + escapeLike(w) + "-%", '\\'))
                        .toArray(Predicate[]::new));
                matches = cb.or(titleContains, allWords);
            }
            if (!Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                Expression<Integer> rank = cb.<Integer>selectCase()
                        .when(cb.like(lowerTitle, text + "%", '\\'), 0)
                        .when(titleContains, 1)
                        .otherwise(2);
                List<Order> orders = List.of(cb.asc(rank), cb.desc(root.get("publishedAt")), cb.desc(root.get("id")));
                query.orderBy(orders);
            }
            return cb.and(published, matches);
        };
        return repository.findAll(spec, PageRequest.of(page - 1, limit)).map(ArticleResponses.Summary::from);
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
