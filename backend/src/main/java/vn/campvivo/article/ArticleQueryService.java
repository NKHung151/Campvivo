package vn.campvivo.article;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.article.dto.ArticleResponses;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/** Đọc bài viết cho module khác (yêu thích — FR-20): chỉ bài đã xuất bản. */
@Service
public class ArticleQueryService {

    private final ArticleRepository repository;

    public ArticleQueryService(ArticleRepository repository) {
        this.repository = repository;
    }

    /** Bài ĐÃ XUẤT BẢN theo id; id không tồn tại / bài nháp bị bỏ. */
    @Transactional(readOnly = true)
    public Map<Long, ArticleResponses.Summary> publishedSummaries(Collection<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(articleIds).stream().filter(Article::isPublished)
                .collect(Collectors.toMap(Article::getId, ArticleResponses.Summary::from));
    }

    @Transactional(readOnly = true)
    public boolean isPublished(Long articleId) {
        return repository.findById(articleId).map(Article::isPublished).orElse(false);
    }
}
