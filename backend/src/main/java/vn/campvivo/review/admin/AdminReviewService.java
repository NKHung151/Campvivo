package vn.campvivo.review.admin;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.catalog.Product;
import vn.campvivo.catalog.ProductRepository;
import vn.campvivo.combo.ProductCombo;
import vn.campvivo.combo.ProductComboRepository;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.review.ModerationStatus;
import vn.campvivo.review.Review;
import vn.campvivo.review.ReviewRepository;
import vn.campvivo.review.admin.AdminReviewResponses.AdminReview;
import vn.campvivo.review.admin.AdminReviewResponses.ItemRef;
import vn.campvivo.review.admin.AdminReviewResponses.Query;
import vn.campvivo.review.admin.AdminReviewResponses.UserRef;
import vn.campvivo.review.moderation.TextNormalizer;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static vn.campvivo.catalog.ProductSpecifications.escapeLike;

/**
 * Quản trị đánh giá (FR-18, D-31) — Employee + Admin; xoá chỉ Admin (chặn ở controller).
 * Ẩn / hiện lại / đánh dấu an toàn không đổi `updatedAt` (chỉ chủ sửa mới đổi).
 */
@Service
public class AdminReviewService {

    /** Chữ thường có dấu tiếng Việt → không dấu, dùng cho `translate()` của PostgreSQL (không cần extension unaccent). */
    private static final String ACCENTED = "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ";
    private static final String PLAIN = TextNormalizer.removeDiacritics(ACCENTED);

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductComboRepository comboRepository;
    private final Clock clock;

    public AdminReviewService(ReviewRepository reviewRepository, UserRepository userRepository,
                              ProductRepository productRepository, ProductComboRepository comboRepository, Clock clock) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.comboRepository = comboRepository;
        this.clock = clock;
    }

    /** Mới nhất trước (`createdAt` rồi `id` giảm dần). */
    @Transactional(readOnly = true)
    public PageResponse<AdminReview> list(Query q) {
        Specification<Review> spec = Specification.where(matchesKeyword(q.q()))
                .and(q.itemType() == null ? null : (root, query, cb) ->
                        "PRODUCT".equals(q.itemType()) ? cb.isNotNull(root.get("productId")) : cb.isNotNull(root.get("comboId")))
                .and(q.rating() == null ? null : (root, query, cb) -> cb.equal(root.get("rating"), q.rating().shortValue()))
                .and(q.status() == null ? null : (root, query, cb) -> switch (q.status()) {
                    case VISIBLE -> cb.isFalse(root.get("hidden"));
                    case HIDDEN -> cb.isTrue(root.get("hidden"));
                    case SENSITIVE -> cb.and(cb.isFalse(root.get("hidden")),
                            cb.equal(root.get("moderationStatus"), ModerationStatus.SENSITIVE));
                });
        Page<Review> result = reviewRepository.findAll(spec,
                PageRequest.of(q.page() - 1, q.limit(), Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return PageResponse.of(result.map(toResponse(result.getContent())));
    }

    /** Ẩn (đang ẩn → ghi đè lý do, người ẩn, thời điểm). */
    @Transactional
    public AdminReview hide(Long id, String reason, Long staffId) {
        Review review = find(id);
        review.hide(reason.strip(), staffId, OffsetDateTime.now(clock));
        return toResponse(List.of(review)).apply(review);
    }

    @Transactional
    public AdminReview unhide(Long id) {
        Review review = find(id);
        review.unhide();
        return toResponse(List.of(review)).apply(review);
    }

    /** SENSITIVE → CLEARED (bỏ làm mờ); trạng thái khác giữ nguyên. */
    @Transactional
    public AdminReview clearSensitive(Long id) {
        Review review = find(id);
        review.clearSensitive();
        return toResponse(List.of(review)).apply(review);
    }

    /** Xoá cứng — khách viết lại được. */
    @Transactional
    public void delete(Long id) {
        reviewRepository.delete(find(id));
    }

    // ------------------------------------------------------------------

    private Review find(Long id) {
        return reviewRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /**
     * `q` khớp chuỗi con (không phân biệt hoa thường, gõ không dấu được) trong email hoặc họ tên khách, hoặc tên mặt hàng.
     * `%`, `_`, `\` là chữ thường, không phải ký tự đại diện.
     */
    static Specification<Review> matchesKeyword(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(fold(q.strip())) + "%";
        return (root, query, cb) -> {
            Subquery<Long> users = query.subquery(Long.class);
            Root<User> u = users.from(User.class);
            users.select(u.get("id")).where(cb.or(
                    cb.like(folded(cb, u.get("email")), pattern, '\\'),
                    cb.like(folded(cb, u.get("fullName")), pattern, '\\')));

            Subquery<Long> products = query.subquery(Long.class);
            Root<Product> p = products.from(Product.class);
            products.select(p.get("id")).where(cb.like(folded(cb, p.get("name")), pattern, '\\'));

            Subquery<Long> combos = query.subquery(Long.class);
            Root<ProductCombo> c = combos.from(ProductCombo.class);
            combos.select(c.get("id")).where(cb.like(folded(cb, c.get("name")), pattern, '\\'));

            return cb.or(root.get("userId").in(users), root.get("productId").in(products), root.get("comboId").in(combos));
        };
    }

    /** Cùng phép biến đổi phía Java của {@link #folded}: chữ thường rồi bỏ dấu. */
    static String fold(String text) {
        return TextNormalizer.removeDiacritics(text.toLowerCase(Locale.ROOT));
    }

    private static Expression<String> folded(CriteriaBuilder cb, Expression<String> column) {
        return cb.function("translate", String.class, cb.lower(column), cb.literal(ACCENTED), cb.literal(PLAIN));
    }

    /** Nạp khách, người ẩn, sản phẩm, combo của cả trang: 1 truy vấn mỗi loại. */
    private Function<Review, AdminReview> toResponse(Collection<Review> reviews) {
        List<Long> userIds = reviews.stream().flatMap(r -> Stream.of(r.getUserId(), r.getHiddenBy()))
                .filter(Objects::nonNull).distinct().toList();
        List<Long> productIds = reviews.stream().map(Review::getProductId).filter(Objects::nonNull).distinct().toList();
        List<Long> comboIds = reviews.stream().map(Review::getComboId).filter(Objects::nonNull).distinct().toList();

        Map<Long, User> userMap = byId(userIds, userRepository::findAllById, User::getId);
        Map<Long, Product> productMap = byId(productIds, productRepository::findAllById, Product::getId);
        Map<Long, ProductCombo> comboMap = byId(comboIds, comboRepository::findAllById, ProductCombo::getId);

        return r -> {
            User owner = userMap.get(r.getUserId());
            UserRef user = owner == null ? null : new UserRef(owner.getId(), owner.getFullName(), owner.getEmail());
            ItemRef item;
            if (r.isForProduct()) {
                Product p = productMap.get(r.getProductId());
                item = new ItemRef("PRODUCT", r.getProductId(), p == null ? null : p.getName(), p == null ? null : p.getSlug());
            } else {
                ProductCombo c = comboMap.get(r.getComboId());
                item = new ItemRef("COMBO", r.getComboId(), c == null ? null : c.getName(), c == null ? null : c.getSlug());
            }
            User hiddenBy = r.getHiddenBy() == null ? null : userMap.get(r.getHiddenBy());
            return AdminReview.from(r, user, item, hiddenBy == null ? null : hiddenBy.getFullName());
        };
    }

    private static <T> Map<Long, T> byId(List<Long> ids, Function<List<Long>, List<T>> load, Function<T, Long> id) {
        return ids.isEmpty() ? Map.of() : load.apply(ids).stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
