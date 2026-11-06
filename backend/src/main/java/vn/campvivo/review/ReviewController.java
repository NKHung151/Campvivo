package vn.campvivo.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.campvivo.auth.CurrentUserId;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.review.dto.MyReviewResponse;
import vn.campvivo.review.dto.ReviewRequests;
import vn.campvivo.review.dto.ReviewResponse;
import vn.campvivo.review.dto.ReviewSummaryResponse;

import java.util.Map;

import static vn.campvivo.review.ReviewQueryService.COMBO;
import static vn.campvivo.review.ReviewQueryService.PRODUCT;

/**
 * Đánh giá — api.md mục 4.3 (công khai: danh sách + tổng quan theo mặt hàng) và 4.6 (khách: viết/sửa/xoá, của mình).
 * Quyền: GET `/api/products/**`, `/api/combos/**` công khai; `/api/reviews/**` chỉ CUSTOMER (SecurityConfig).
 */
@RestController
@Validated
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewQueryService reviewQueryService;

    public ReviewController(ReviewService reviewService, ReviewQueryService reviewQueryService) {
        this.reviewService = reviewService;
        this.reviewQueryService = reviewQueryService;
    }

    @GetMapping("/api/products/{id}/reviews")
    public PageResponse<ReviewResponse> productReviews(
            @PathVariable Long id,
            @RequestParam(required = false) @Min(value = 1, message = "Số sao từ 1 đến 5.")
            @Max(value = 5, message = "Số sao từ 1 đến 5.") Integer rating,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "Số đánh giá mỗi trang từ 1 đến 100.")
            @Max(value = 100, message = "Số đánh giá mỗi trang từ 1 đến 100.") int limit) {
        return reviewQueryService.list(PRODUCT, id, rating, page, limit);
    }

    @GetMapping("/api/products/{id}/reviews/summary")
    public ApiResponse<ReviewSummaryResponse> productSummary(@PathVariable Long id) {
        return ApiResponse.of(reviewQueryService.summary(PRODUCT, id));
    }

    @GetMapping("/api/combos/{id}/reviews")
    public PageResponse<ReviewResponse> comboReviews(
            @PathVariable Long id,
            @RequestParam(required = false) @Min(value = 1, message = "Số sao từ 1 đến 5.")
            @Max(value = 5, message = "Số sao từ 1 đến 5.") Integer rating,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "Số đánh giá mỗi trang từ 1 đến 100.")
            @Max(value = 100, message = "Số đánh giá mỗi trang từ 1 đến 100.") int limit) {
        return reviewQueryService.list(COMBO, id, rating, page, limit);
    }

    @GetMapping("/api/combos/{id}/reviews/summary")
    public ApiResponse<ReviewSummaryResponse> comboSummary(@PathVariable Long id) {
        return ApiResponse.of(reviewQueryService.summary(COMBO, id));
    }

    /** Đánh giá của chính khách cho 1 mặt hàng (`data: null` khi chưa viết). Đúng một trong productId, comboId. */
    @GetMapping("/api/reviews/mine")
    public ApiResponse<MyReviewResponse> mine(@CurrentUserId Long userId,
                                              @RequestParam(required = false) Long productId,
                                              @RequestParam(required = false) Long comboId) {
        requireExactlyOne(productId, comboId);
        return productId != null
                ? ApiResponse.of(reviewQueryService.mine(userId, PRODUCT, productId))
                : ApiResponse.of(reviewQueryService.mine(userId, COMBO, comboId));
    }

    /** Khách viết được đánh giá cho mặt hàng ngay lúc này không (đã nhận hàng + chưa viết) — trang sản phẩm/combo. */
    @GetMapping("/api/reviews/eligibility")
    public ApiResponse<Eligibility> eligibility(@CurrentUserId Long userId,
                                                @RequestParam(required = false) Long productId,
                                                @RequestParam(required = false) Long comboId) {
        requireExactlyOne(productId, comboId);
        return ApiResponse.of(new Eligibility(productId != null
                ? reviewService.canReview(userId, PRODUCT, productId)
                : reviewService.canReview(userId, COMBO, comboId)));
    }

    public record Eligibility(boolean canReview) {}

    private static void requireExactlyOne(Long productId, Long comboId) {
        if ((productId == null) == (comboId == null)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    Map.of("fieldErrors", Map.of("productId", "Chọn đúng một sản phẩm hoặc một combo")));
        }
    }

    @PostMapping("/api/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MyReviewResponse> create(@CurrentUserId Long userId,
                                                @Valid @RequestBody ReviewRequests.Create request) {
        return ApiResponse.of(reviewService.create(userId, request));
    }

    @PutMapping("/api/reviews/{id}")
    public ApiResponse<MyReviewResponse> update(@CurrentUserId Long userId, @PathVariable Long id,
                                                @Valid @RequestBody ReviewRequests.Update request) {
        return ApiResponse.of(reviewService.update(userId, id, request));
    }

    @DeleteMapping("/api/reviews/{id}")
    public ApiResponse<Void> delete(@CurrentUserId Long userId, @PathVariable Long id) {
        reviewService.delete(userId, id);
        return ApiResponse.of(null);
    }
}
