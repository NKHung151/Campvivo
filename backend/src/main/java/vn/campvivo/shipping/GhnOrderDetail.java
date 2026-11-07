package vn.campvivo.shipping;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Chi tiết vận đơn GHN. `status` là mã trạng thái của GHN (ready_to_pick, picking, delivering, delivered, cancel...),
 * `log` là lịch sử đổi trạng thái theo thời gian.
 */
public record GhnOrderDetail(
        String orderCode,
        String clientOrderCode,
        String status,
        OffsetDateTime expectedDeliveryTime,
        OffsetDateTime updatedAt,
        List<LogEntry> log
) {
    public record LogEntry(String status, OffsetDateTime updatedAt) {}
}
