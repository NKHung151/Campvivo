package vn.campvivo.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.order.dto.OrderDetailResponse;
import vn.campvivo.order.dto.OrderSummaryResponse;
import vn.campvivo.payment.PaymentService;
import vn.campvivo.review.ReviewQueryService;

import java.util.List;

/** Đọc đơn hàng (FR-12). Đơn của người khác → 404 NOT_FOUND (không tiết lộ đơn tồn tại). */
@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final ReviewQueryService reviewQueryService;

    public OrderQueryService(OrderRepository orderRepository, PaymentService paymentService,
                             ReviewQueryService reviewQueryService) {
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
        this.reviewQueryService = reviewQueryService;
    }

    /** Đơn của chính khách, mới nhất trước, lọc theo trạng thái (FR-12). */
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listForCustomer(Long userId, OrderStatus status, int page, int limit) {
        Specification<Order> spec = (root, query, cb) -> cb.equal(root.get("userId"), userId);
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        Page<Order> result = orderRepository.findAll(spec,
                PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return PageResponse.of(result.map(OrderSummaryResponse::from));
    }

    /**
     * Thống kê đơn của một khách cho trang chi tiết người dùng của Admin (FR-14): tổng số đơn (mọi trạng thái), số đơn
     * COMPLETED và tổng tiền các đơn COMPLETED. Đơn đã hoàn trả (RETURNED) không tính vào chi tiêu.
     */
    @Transactional(readOnly = true)
    public UserOrderStats statsForUser(Long userId) {
        return new UserOrderStats(
                orderRepository.countByUserId(userId),
                orderRepository.countByUserIdAndStatus(userId, OrderStatus.COMPLETED),
                orderRepository.sumFinalAmountByUserIdAndStatus(userId, OrderStatus.COMPLETED));
    }

    /** BR-10 — điều kiện viết đánh giá. `itemType`: "PRODUCT" / "COMBO". */
    @Transactional(readOnly = true)
    public boolean hasCompletedPurchase(Long userId, String itemType, Long itemId) {
        return "COMBO".equals(itemType)
                ? orderRepository.existsCompletedPurchaseOfCombo(userId, itemId)
                : orderRepository.existsCompletedPurchaseOfProduct(userId, itemId);
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getForCustomer(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        // myReviewId cho mọi dòng: 1 truy vấn cho sản phẩm + 1 cho combo
        List<Long> productIds = order.getItems().stream().filter(OrderItem::isProduct).map(OrderItem::getProductId).toList();
        List<Long> comboIds = order.getItems().stream().filter(i -> !i.isProduct()).map(OrderItem::getComboId).toList();
        ReviewQueryService.ReviewIds reviews = reviewQueryService.reviewIds(userId, productIds, comboIds);
        return OrderDetailResponse.from(order, paymentService.findByOrderId(order.getId()).orElse(null), reviews::of);
    }
}
