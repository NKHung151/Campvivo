package vn.campvivo.order.dto;

import vn.campvivo.payment.PaymentMethod;

import java.math.BigDecimal;

/**
 * Thông tin tối thiểu của một đơn để dựng thông báo (D-42) — module `notification` đọc qua `OrderQueryService`, không đụng
 * repository của `order`. `lastNote` = ghi chú của lần đổi trạng thái mới nhất (vd. lý do huỷ).
 */
public record OrderNotificationInfo(Long orderId, String orderCode, Long customerId, PaymentMethod paymentMethod,
                                    BigDecimal finalAmount, int itemCount, String lastNote) {
}
