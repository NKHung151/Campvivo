package vn.campvivo.review;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.order.OrderQueryService;
import vn.campvivo.review.dto.MyReviewResponse;
import vn.campvivo.review.dto.ReviewRequests;
import vn.campvivo.review.moderation.ContentModerator;
import vn.campvivo.review.moderation.ModerationResult;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

import static vn.campvivo.review.ReviewQueryService.COMBO;
import static vn.campvivo.review.ReviewQueryService.PRODUCT;

/**
 * Viết / sửa / xoá đánh giá của chính khách (FR-18, BR-10). Mỗi lần viết/sửa chạy kiểm duyệt từ điển (D-33) —
 * chỉ gắn cờ làm mờ, không chặn. Đánh giá bị ẩn chỉ xoá được (D-31).
 */
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewQueryService reviewQueryService;
    private final OrderQueryService orderQueryService;
    private final ContentModerator moderator;
    private final Clock clock;

    public ReviewService(ReviewRepository reviewRepository, ReviewQueryService reviewQueryService,
                         OrderQueryService orderQueryService, ContentModerator moderator, Clock clock) {
        this.reviewRepository = reviewRepository;
        this.reviewQueryService = reviewQueryService;
        this.orderQueryService = orderQueryService;
        this.moderator = moderator;
        this.clock = clock;
    }

    /** Thứ tự kiểm tra: mặt hàng đang bán → đã mua (COMPLETED) → chưa có đánh giá. */
    @Transactional
    public MyReviewResponse create(Long userId, ReviewRequests.Create req) {
        String itemType = req.productId() != null ? PRODUCT : COMBO;
        Long itemId = req.productId() != null ? req.productId() : req.comboId();
        reviewQueryService.requireSellable(itemType, itemId);
        if (!orderQueryService.hasCompletedPurchase(userId, itemType, itemId)) {
            throw new BusinessException(ErrorCode.NOT_PURCHASED);
        }
        Optional<Review> existing = PRODUCT.equals(itemType)
                ? reviewRepository.findByUserIdAndProductId(userId, itemId)
                : reviewRepository.findByUserIdAndComboId(userId, itemId);
        if (existing.isPresent()) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        Review review = PRODUCT.equals(itemType)
                ? Review.forProduct(userId, itemId, now)
                : Review.forCombo(userId, itemId, now);
        String content = normalizeContent(req.content());
        ModerationResult moderation = moderator.check(content);
        review.write(req.rating(), content, moderation.sensitive(), moderation.flaggedTerms());
        try {
            reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException ex) {
            // Hai yêu cầu gửi gần như cùng lúc: unique index một phần là chốt cuối
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
        return reviewQueryService.toMine(review);
    }

    /** Viết được ngay lúc này: đã có đơn COMPLETED chứa mặt hàng và chưa có đánh giá (cùng điều kiện với {@link #create}). */
    @Transactional(readOnly = true)
    public boolean canReview(Long userId, String itemType, Long itemId) {
        boolean reviewed = (PRODUCT.equals(itemType)
                ? reviewRepository.findByUserIdAndProductId(userId, itemId)
                : reviewRepository.findByUserIdAndComboId(userId, itemId)).isPresent();
        return !reviewed && orderQueryService.hasCompletedPurchase(userId, itemType, itemId);
    }

    @Transactional
    public MyReviewResponse update(Long userId, Long reviewId, ReviewRequests.Update req) {
        Review review = owned(userId, reviewId);
        if (review.isHidden()) {
            throw new BusinessException(ErrorCode.REVIEW_HIDDEN);
        }
        String content = normalizeContent(req.content());
        ModerationResult moderation = moderator.check(content);
        review.edit(req.rating(), content, moderation.sensitive(), moderation.flaggedTerms(), OffsetDateTime.now(clock));
        return reviewQueryService.toMine(review);
    }

    /** Xoá cứng, được cả khi đang bị ẩn; xoá xong viết lại được (D-09). */
    @Transactional
    public void delete(Long userId, Long reviewId) {
        reviewRepository.delete(owned(userId, reviewId));
    }

    private Review owned(Long userId, Long reviewId) {
        return reviewRepository.findByIdAndUserId(reviewId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** `trim`; rỗng / toàn khoảng trắng → null (BR-10). */
    static String normalizeContent(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
