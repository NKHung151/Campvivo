package vn.campvivo.review;

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
import vn.campvivo.review.dto.MyReviewResponse;
import vn.campvivo.review.dto.ReviewResponse;
import vn.campvivo.review.dto.ReviewSummaryResponse;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Đọc đánh giá (FR-18): danh sách + tổng quan công khai (bỏ đánh giá bị ẩn — D-31), đánh giá của chính khách.
 * Không phụ thuộc module order (order gọi sang đây để gắn `myReviewId` vào chi tiết đơn).
 */
@Service
public class ReviewQueryService {

    public static final String PRODUCT = "PRODUCT";
    public static final String COMBO = "COMBO";

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final ProductComboRepository comboRepository;
    private final UserRepository userRepository;

    public ReviewQueryService(ReviewRepository reviewRepository, ProductRepository productRepository,
                              ProductComboRepository comboRepository, UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.comboRepository = comboRepository;
        this.userRepository = userRepository;
    }

    /** Mới nhất trước; `rating` null = mọi mức sao. Mặt hàng không tồn tại/đang tắt → NOT_FOUND. */
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> list(String itemType, Long itemId, Integer rating, int page, int limit) {
        requireSellable(itemType, itemId);
        String itemField = PRODUCT.equals(itemType) ? "productId" : "comboId";
        Specification<Review> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get(itemField), itemId), cb.isFalse(root.get("hidden")));
        if (rating != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("rating"), rating.shortValue()));
        }
        Page<Review> result = reviewRepository.findAll(spec,
                PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        Map<Long, String> names = fullNames(result.getContent().stream().map(Review::getUserId).toList());
        return PageResponse.of(result.map(r -> ReviewResponse.from(r, names.get(r.getUserId()))));
    }

    @Transactional(readOnly = true)
    public ReviewSummaryResponse summary(String itemType, Long itemId) {
        requireSellable(itemType, itemId);
        return ReviewSummaryResponse.fromCounts(PRODUCT.equals(itemType)
                ? reviewRepository.countByRatingForProduct(itemId)
                : reviewRepository.countByRatingForCombo(itemId));
    }

    /** Đánh giá của chính khách cho 1 mặt hàng, kể cả khi bị ẩn; chưa viết → null. */
    @Transactional(readOnly = true)
    public MyReviewResponse mine(Long userId, String itemType, Long itemId) {
        return (PRODUCT.equals(itemType)
                ? reviewRepository.findByUserIdAndProductId(userId, itemId)
                : reviewRepository.findByUserIdAndComboId(userId, itemId))
                .map(this::toMine)
                .orElse(null);
    }

    /** id đánh giá của khách theo sản phẩm / combo — chi tiết đơn (`myReviewId`), 1 truy vấn mỗi loại. */
    @Transactional(readOnly = true)
    public ReviewIds reviewIds(Long userId, Collection<Long> productIds, Collection<Long> comboIds) {
        Map<Long, Long> byProduct = productIds.isEmpty() ? Map.of()
                : reviewRepository.findByUserIdAndProductIdIn(userId, productIds).stream()
                        .collect(Collectors.toMap(Review::getProductId, Review::getId));
        Map<Long, Long> byCombo = comboIds.isEmpty() ? Map.of()
                : reviewRepository.findByUserIdAndComboIdIn(userId, comboIds).stream()
                        .collect(Collectors.toMap(Review::getComboId, Review::getId));
        return new ReviewIds(byProduct, byCombo);
    }

    public record ReviewIds(Map<Long, Long> byProduct, Map<Long, Long> byCombo) {
        public Long of(String itemType, Long itemId) {
            return (PRODUCT.equals(itemType) ? byProduct : byCombo).get(itemId);
        }
    }

    MyReviewResponse toMine(Review r) {
        return MyReviewResponse.from(r, fullNames(List.of(r.getUserId())).get(r.getUserId()));
    }

    /** Mặt hàng phải tồn tại và đang bán (cùng quy tắc `GET /products/{slug}`). */
    void requireSellable(String itemType, Long itemId) {
        boolean sellable = PRODUCT.equals(itemType)
                ? productRepository.findById(itemId).map(Product::isActive).orElse(false)
                : comboRepository.findById(itemId).map(ProductCombo::isActive).orElse(false);
        if (!sellable) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    private Map<Long, String> fullNames(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return new HashMap<>(userRepository.findAllById(userIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName, (a, b) -> a)));
    }
}
