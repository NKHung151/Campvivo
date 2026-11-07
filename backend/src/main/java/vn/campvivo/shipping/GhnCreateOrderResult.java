package vn.campvivo.shipping;

import java.time.OffsetDateTime;

/** Kết quả tạo vận đơn: mã vận đơn GHN, phí GHN tính, thời gian giao dự kiến (có thể null). */
public record GhnCreateOrderResult(String orderCode, long totalFee, OffsetDateTime expectedDeliveryTime) {}
