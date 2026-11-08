package vn.campvivo.article;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.campvivo.article.dto.ArticleResponses;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;

/**
 * Bài viết public (FR-21a) — api.md mục 4.3. Chỉ bài đã xuất bản, mới nhất trước; bài nháp trả NOT_FOUND.
 */
@RestController
@RequestMapping("/api/articles")
@Validated
public class ArticleController {

    private static final Sort NEWEST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));

    private final ArticleRepository repository;

    public ArticleController(ArticleRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<ArticleResponses.Summary> list(
            @RequestParam(required = false) ArticleCategory category,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "12") @Min(value = 1, message = "Số bài mỗi trang từ 1 đến 50.")
            @Max(value = 50, message = "Số bài mỗi trang từ 1 đến 50.") int limit) {
        PageRequest pageable = PageRequest.of(page - 1, limit, NEWEST);
        var result = category == null ? repository.findByPublishedTrue(pageable)
                : repository.findByPublishedTrueAndCategory(category, pageable);
        return PageResponse.of(result.map(ArticleResponses.Summary::from));
    }

    @GetMapping("/{slug}")
    @Transactional(readOnly = true)
    public ApiResponse<ArticleResponses.Detail> get(@PathVariable String slug) {
        return ApiResponse.of(repository.findBySlugAndPublishedTrue(slug)
                .map(ArticleResponses.Detail::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)));
    }
}
