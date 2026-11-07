package vn.campvivo.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.payment.PaymentMethod;
import vn.campvivo.shipping.GhnCreateOrderRequest;
import vn.campvivo.shipping.GhnCreateOrderResult;
import vn.campvivo.shipping.GhnService;
import vn.campvivo.shipping.ShippingService;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Tạo vận đơn GHN cho đơn đã xác nhận (D-27, D-28). Gọi tự động sau khi đơn chuyển CONFIRMED (OrderShipmentListener)
 * và gọi lại được (nhân viên bấm "Tạo vận đơn" khi lần trước lỗi). Idempotent: đơn đã có mã vận đơn thì bỏ qua;
 * GHN cũng chặn trùng theo `client_order_code` = mã đơn.
 * COD: GHN thu hộ `final_amount`; VNPay đã thanh toán: thu hộ 0. Lỗi GHN không làm hỏng đơn — ghi `shipping_order_error`.
 */
@Service
public class OrderShipmentService {

    private static final Logger log = LoggerFactory.getLogger(OrderShipmentService.class);
    private static final Set<OrderStatus> SHIPPABLE = EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPING);

    private final OrderStateService orderStateService;
    private final GhnService ghnService;
    private final ShippingService shippingService;
    private final Clock clock;

    public OrderShipmentService(OrderStateService orderStateService, GhnService ghnService,
                                ShippingService shippingService, Clock clock) {
        this.orderStateService = orderStateService;
        this.ghnService = ghnService;
        this.shippingService = shippingService;
        this.clock = clock;
    }

    /**
     * Trả về đơn sau khi thử tạo vận đơn. `rethrow` = true (thao tác của nhân viên) thì lỗi GHN được ném ra để giao diện
     * báo; false (tự động) thì chỉ ghi lại lỗi.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public Order createShipment(Long orderId, boolean rethrow) {
        // Khoá đơn: hai lần gọi song song (listener + nhân viên) không tạo hai vận đơn
        Order order = orderStateService.lock(orderId);
        if (order.getShippingOrderCode() != null) {
            return order;
        }
        if (!SHIPPABLE.contains(order.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        try {
            GhnCreateOrderResult result = ghnService.createOrder(toGhnRequest(order));
            order.recordShipment(result.orderCode(), OffsetDateTime.now(clock));
            log.info("Đã tạo vận đơn GHN {} cho đơn {}", result.orderCode(), order.getOrderCode());
        } catch (BusinessException e) {
            order.recordShipmentError(e.getMessage(), OffsetDateTime.now(clock));
            log.warn("Tạo vận đơn GHN cho đơn {} thất bại: {}", order.getOrderCode(), e.getErrorCode());
            if (rethrow) {
                throw e;
            }
        }
        return order;
    }

    private GhnCreateOrderRequest toGhnRequest(Order order) {
        int itemWeight = shippingService.weightOf(1);
        long codAmount = order.getPaymentMethod() == PaymentMethod.COD ? order.getFinalAmount().longValueExact() : 0;
        return new GhnCreateOrderRequest(
                order.getOrderCode(),
                order.getRecipientName(),
                order.getRecipientPhone(),
                order.getShippingAddress(),
                order.getShippingWardCode(),
                order.getShippingDistrictId(),
                codAmount,
                0,
                shippingService.weightOf(order.totalQuantity()),
                order.getNote(),
                order.getItems().stream().map(i -> new GhnCreateOrderRequest.Item(
                        i.getItemName(),
                        (i.isProduct() ? "SP-" + i.getProductId() : "CB-" + i.getComboId()),
                        i.getQuantity(),
                        i.getUnitPrice().longValueExact(),
                        itemWeight)).toList());
    }
}
