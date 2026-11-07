package vn.campvivo.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.order.event.OrderStatusChangedEvent;

/**
 * Đơn vừa CONFIRMED (VNPay thanh toán xong / nhân viên xác nhận COD) → tạo vận đơn GHN. Chạy SAU commit và bất đồng bộ
 * (D-15): IPN/Return URL trả lời ngay, GHN chậm hay lỗi cũng không ảnh hưởng trạng thái đơn đã thanh toán.
 * Tắt bằng app.ghn.auto-create-shipment=false (vd. môi trường chưa có GHN).
 */
@Component
public class OrderShipmentListener {

    private static final Logger log = LoggerFactory.getLogger(OrderShipmentListener.class);

    private final OrderShipmentService shipmentService;
    private final boolean enabled;

    public OrderShipmentListener(OrderShipmentService shipmentService, AppProperties appProperties) {
        this.shipmentService = shipmentService;
        this.enabled = appProperties.ghn().autoCreateShipment();
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(OrderStatusChangedEvent event) {
        if (!enabled || event.newStatus() != OrderStatus.CONFIRMED) {
            return;
        }
        try {
            shipmentService.createShipment(event.orderId(), false);
        } catch (RuntimeException e) {
            log.error("Không tạo được vận đơn cho đơn id={}", event.orderId(), e);
        }
    }
}
