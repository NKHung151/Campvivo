package vn.campvivo.article.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.article.Article;
import vn.campvivo.article.ArticleRepository;
import vn.campvivo.article.admin.AdminArticleResponses.Detail;
import vn.campvivo.article.admin.AdminArticleResponses.Summary;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.common.util.HtmlSanitizer;
import vn.campvivo.common.util.SlugUtil;
import vn.campvivo.user.UserRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static vn.campvivo.catalog.ProductSpecifications.escapeLike;
import static vn.campvivo.catalog.ProductSpecifications.isCountQuery;

/**
 * Quản lý bài viết — FR-21b.
 * <ul>
 *   <li>Slug trống → tạo mới sinh từ tiêu đề (trùng thêm -2, -3…), sửa giữ slug cũ; tự nhập mà trùng → SLUG_ALREADY_EXISTS.</li>
 *   <li>Nội dung HTML được làm sạch trước khi lưu (chống XSS).</li>
 *   <li>Nháp lưu được khi còn thiếu; xuất bản bắt buộc có tóm tắt, ảnh đại diện và nội dung (400 fieldErrors).</li>
 *   <li>Ngày xuất bản gán ở lần xuất bản đầu tiên, gỡ xuống rồi đăng lại vẫn giữ.</li>
 *   <li>Tác giả = admin tạo bài (không đổi khi người khác sửa). Xoá là xoá cứng.</li>
 * </ul>
 */
@Service
public class AdminArticleService {

    private static final String DEFAULT_SLUG = "bai-viet";
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern ENTITY_SPACE = Pattern.compile("&nbsp;|&#160;");

    private final ArticleRepository repository;
    private final UserRepository users;
    private final Clock clock;

    public AdminArticleService(ArticleRepository repository, UserRepository users, Clock clock) {
        this.repository = repository;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<Summary> list(AdminArticleQuery q) {
        Specification<Article> spec = Specification.<Article>where(withAuthorFetched())
                .and(matchesKeyword(q.q()))
                .and(q.category() == null ? null : (root, query, cb) -> cb.equal(root.get("category"), q.category()))
                .and(q.isPublished() == null ? null : (root, query, cb) -> cb.equal(root.get("published"), q.isPublished()));
        Page<Article> page = repository.findAll(spec, PageRequest.of(q.page() - 1, q.limit(), q.sort().toSort()));
        return PageResponse.of(page.map(Summary::from));
    }

    @Transactional(readOnly = true)
    public Detail get(Long id) {
        return Detail.from(find(id));
    }

    @Transactional
    public Detail create(ArticleRequest req, Long authorId) {
        Fields f = clean(req);
        if (req.isPublished()) {
            requireReadyToPublish(f);
        }
        String slug = isBlank(req.slug()) ? uniqueSlugFrom(f.title()) : requireFreeSlug(req.slug(), null);
        OffsetDateTime now = OffsetDateTime.now(clock);
        Article article = new Article(users.getReferenceById(authorId), now);
        article.updateDetails(f.title(), slug, f.summary(), f.content(), f.thumbnailUrl(), req.category(), now);
        article.setPublished(req.isPublished(), now);
        return Detail.from(repository.save(article));
    }

    @Transactional
    public Detail update(Long id, ArticleRequest req) {
        Article article = find(id);
        Fields f = clean(req);
        if (req.isPublished()) {
            requireReadyToPublish(f);
        }
        String slug = isBlank(req.slug()) ? article.getSlug() : requireFreeSlug(req.slug(), id);
        OffsetDateTime now = OffsetDateTime.now(clock);
        article.updateDetails(f.title(), slug, f.summary(), f.content(), f.thumbnailUrl(), req.category(), now);
        article.setPublished(req.isPublished(), now);
        return Detail.from(article);
    }

    /** Xuất bản / gỡ nhanh từ danh sách; xuất bản vẫn phải đủ tóm tắt, ảnh, nội dung. */
    @Transactional
    public Summary changeStatus(Long id, boolean published) {
        Article article = find(id);
        if (published) {
            requireReadyToPublish(new Fields(article.getTitle(), article.getSummary(), article.getContent(),
                    article.getThumbnailUrl()));
        }
        article.setPublished(published, OffsetDateTime.now(clock));
        return Summary.from(article);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    // ---------------------------------------------------------------------------------------------

    private record Fields(String title, String summary, String content, String thumbnailUrl) {}

    private static Fields clean(ArticleRequest req) {
        String content = req.content() == null ? "" : HtmlSanitizer.sanitize(req.content()).trim();
        return new Fields(req.title().trim(), blankToNull(req.summary()), content, blankToNull(req.thumbnailUrl()));
    }

    private static void requireReadyToPublish(Fields f) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (f.summary() == null) {
            errors.put("summary", "Cần nhập tóm tắt trước khi xuất bản.");
        }
        if (f.thumbnailUrl() == null) {
            errors.put("thumbnailUrl", "Cần chọn ảnh đại diện trước khi xuất bản.");
        }
        if (!hasContent(f.content())) {
            errors.put("content", "Cần viết nội dung trước khi xuất bản.");
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                    Map.of("fieldErrors", errors));
        }
    }

    /** Có chữ hoặc có ảnh (bỏ thẻ HTML và khoảng trắng). */
    private static boolean hasContent(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        // Bộ làm sạch giữ &nbsp; dạng entity hoặc ký tự U+00A0 — cả hai đều chỉ là khoảng trắng
        String text = ENTITY_SPACE.matcher(TAGS.matcher(html).replaceAll(" ")).replaceAll(" ").replace(' ', ' ').trim();
        return !text.isEmpty() || html.contains("<img");
    }

    private Article find(Long id) {
        return repository.findWithAuthorById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private String requireFreeSlug(String slug, Long excludeId) {
        boolean taken = excludeId == null ? repository.existsBySlug(slug) : repository.existsBySlugAndIdNot(slug, excludeId);
        if (taken) {
            throw new BusinessException(ErrorCode.SLUG_ALREADY_EXISTS);
        }
        return slug;
    }

    private String uniqueSlugFrom(String title) {
        String base = SlugUtil.toSlug(title);
        if (base.isEmpty()) {
            base = DEFAULT_SLUG;
        }
        if (base.length() > 270) {
            base = base.substring(0, 270).replaceAll("-+$", "");
        }
        String candidate = base;
        for (int n = 2; repository.existsBySlug(candidate); n++) {
            candidate = base + "-" + n;
        }
        return candidate;
    }

    private static Specification<Article> withAuthorFetched() {
        return (root, query, cb) -> {
            if (!isCountQuery(query)) {
                root.fetch("author");
            }
            return null;
        };
    }

    /** Tìm theo tiêu đề hoặc slug; gõ không dấu được: mỗi từ phải khớp trọn từ trong slug. */
    private static Specification<Article> matchesKeyword(String q) {
        if (isBlank(q)) {
            return null;
        }
        String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
        List<String> words = Arrays.stream(SlugUtil.toSlug(q).split("-")).filter(w -> !w.isEmpty()).toList();
        return (root, query, cb) -> {
            var exact = cb.or(cb.like(cb.lower(root.get("title")), pattern, '\\'), cb.like(root.get("slug"), pattern, '\\'));
            if (words.isEmpty()) {
                return exact;
            }
            var dashedSlug = cb.concat(cb.concat("-", root.<String>get("slug")), "-");
            var allWords = cb.and(words.stream()
                    .map(w -> cb.like(dashedSlug, "%-" + escapeLike(w) + "-%", '\\'))
                    .toArray(jakarta.persistence.criteria.Predicate[]::new));
            return cb.or(exact, allWords);
        };
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
