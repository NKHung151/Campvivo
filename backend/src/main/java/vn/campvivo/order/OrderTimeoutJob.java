package vn.campvivo.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.payment.PaymentMethod;
import vn.campvivo.payment.PaymentService;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * BR-3: huỷ đơn thanh toán online còn PENDING quá app.order.payment-timeout (mặc định 15 phút, trùng vnp_ExpireDate).
 * Quét mỗi app.order.timeout-job-delay; mỗi đơn một transaction, khoá lại và kiểm tra còn PENDING mới huỷ. Đơn COD không
 * có hạn.
 */
@Component
public class OrderTimeoutJob implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutJob.class);

    private final OrderRepository orderRepository;
    private final OrderStateService orderStateService;
    private final PaymentService paymentService;
    private final AppProperties.Order props;
    private final Clock clock;

    public OrderTimeoutJob(OrderRepository orderRepository, OrderStateService orderStateService,
                           PaymentService paymentService, AppProperties appProperties, Clock clock) {
        this.orderRepository = orderRepository;
        this.orderStateService = orderStateService;
        this.paymentService = paymentService;
        this.props = appProperties.order();
        this.clock = clock;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(this::cancelExpiredOrders, props.timeoutJobDelay());
    }

    /** Trả về số đơn đã huỷ. */
    public int cancelExpiredOrders() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minus(props.paymentTimeout());
        int cancelled = 0;
        for (PaymentMethod method : PaymentMethod.values()) {
            if (!paymentService.requiresGateway(method)) {
                continue;
            }
            for (Long id : orderRepository.findPendingIdsCreatedBefore(method, cutoff)) {
                try {
                    if (orderStateService.expireIfStillPending(id, cutoff)) {
                        cancelled++;
                    }
                } catch (RuntimeException e) {
                    log.error("Không huỷ được đơn quá hạn id={}", id, e);
                }
            }
        }
        if (cancelled > 0) {
            log.info("Đã huỷ {} đơn quá hạn thanh toán", cancelled);
        }
        return cancelled;
    }
}
