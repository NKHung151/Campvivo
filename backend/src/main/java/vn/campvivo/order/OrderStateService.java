package vn.campvivo.order;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.inventory.InventoryService;
import vn.campvivo.order.event.OrderStatusChangedEvent;
import vn.campvivo.payment.PaymentService;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Nơi DUY NHẤT đổi `orders.status` (database.md mục 5.1, BR-2, BR-7): khoá đơn, kiểm máy trạng thái, áp hiệu ứng tồn
 * kho/thanh toán, ghi lịch sử, phát OrderStatusChangedEvent (side-effect chạy sau commit).
 * Quyền "ai được chuyển" (khách huỷ đơn mình, nhân viên chỉ xác nhận COD...) kiểm ở service gọi tới.
 */
@Service
public class OrderStateService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public OrderStateService(OrderRepository orderRepository, InventoryService inventoryService,
                             PaymentService paymentService, ApplicationEventPublisher events, Clock clock) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.events = events;
        this.clock = clock;
    }

    /** Khoá đơn theo mã (vnp_TxnRef) — dùng khi xử lý kết quả thanh toán. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Order> lockByCode(String orderCode) {
        return orderCode == null ? Optional.empty() : orderRepository.findByOrderCodeForUpdate(orderCode);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Order lock(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** Khoá đơn rồi chuyển trạng thái. `actorId` null = System. */
    @Transactional
    public Order changeStatus(Long orderId, OrderStatus to, Long actorId, String note) {
        return apply(lock(orderId), to, actorId, note);
    }

    /**
     * Chuyển trạng thái cho đơn ĐÃ khoá trong transaction hiện tại. Sai máy trạng thái → INVALID_STATUS_TRANSITION.
     * Hiệu ứng (BR-2): CONFIRMED trừ kho; CANCELLED/PAYMENT_FAILED trả giữ chỗ; COMPLETED gán completed_at, COD đã thu.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Order apply(Order order, OrderStatus to, Long actorId, String note) {
        OrderStatus from = order.getStatus();
        if (!from.canTransitionTo(to)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        switch (to) {
            case CONFIRMED -> inventoryService.commit(order.stockLines());
            // Trả lượt voucher (BR-5) thêm ở đây khi có module promotion
            case CANCELLED, PAYMENT_FAILED -> inventoryService.release(order.stockLines());
            case COMPLETED -> paymentService.markCodCollected(order.getId());
            default -> { }
        }
        order.moveTo(to, actorId, note, OffsetDateTime.now(clock));
        events.publishEvent(new OrderStatusChangedEvent(order.getId(), from, to, actorId));
        return order;
    }

    /**
     * Job BR-3: huỷ đơn cổng thanh toán còn PENDING sau khi khoá lại (IPN có thể vừa xử lý xong). Transaction riêng
     * cho từng đơn để một đơn lỗi không chặn các đơn khác.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireIfStillPending(Long orderId, OffsetDateTime cutoff) {
        Order order = lock(orderId);
        if (order.getStatus() != OrderStatus.PENDING || !order.getCreatedAt().isBefore(cutoff)) {
            return false;
        }
        apply(order, OrderStatus.CANCELLED, null, "Hết hạn thanh toán");
        return true;
    }
}
