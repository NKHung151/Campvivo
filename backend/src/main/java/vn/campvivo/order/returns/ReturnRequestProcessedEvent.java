package vn.campvivo.order.returns;

import java.math.BigDecimal;

/**
 * Nhân viên vừa duyệt (APPROVED) hoặc từ chối (REJECTED) yêu cầu trả hàng. Listener xử lý SAU commit (D-15) — vd. thông báo
 * `RETURN_STATUS` cho khách và đánh dấu `RETURN_NEW` đã xử lý (D-42, D-43).
 */
public record ReturnRequestProcessedEvent(Long returnId, Long orderId, String orderCode, Long customerId, ReturnStatus status,
                                          String note, BigDecimal refundAmount, Long staffId) {
}
