package vn.campvivo.order.returns.dto;

import vn.campvivo.order.returns.ReturnReason;
import vn.campvivo.order.returns.ReturnRequest;
import vn.campvivo.order.returns.ReturnStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** `ReturnRequestDto` (api.md 4.5, D-41). `note` = ghi chú của nhân viên khi duyệt / từ chối. */
public record ReturnRequestResponse(
        Long id,
        Long orderId,
        ReturnReason reason,
        String reasonLabel,
        String description,
        List<String> imageUrls,
        BigDecimal refundAmount,
        ReturnStatus status,
        String note,
        OffsetDateTime processedAt,
        OffsetDateTime createdAt
) {
    public static ReturnRequestResponse from(ReturnRequest r) {
        return new ReturnRequestResponse(r.getId(), r.getOrderId(), r.getReason(), r.getReason().getLabel(),
                r.getDescription(), List.copyOf(r.getImageUrls()), r.getRefundAmount(), r.getStatus(), r.getNote(),
                r.getProcessedAt(), r.getCreatedAt());
    }
}
