package vn.campvivo.order;

import java.util.EnumSet;
import java.util.Set;

/**
 * Máy trạng thái đơn hàng — database.md mục 5.1. Bảng chuyển hợp lệ chỉ định nghĩa ở đây (không rải `if` trong service).
 * Ai được chuyển (khách / nhân viên / System) và điều kiện thêm (vd. chỉ đơn COD được xác nhận tay) kiểm ở service.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPING,
    COMPLETED,
    CANCELLED,
    PAYMENT_FAILED,
    RETURNED;

    public boolean canTransitionTo(OrderStatus to) {
        return allowedNext().contains(to);
    }

    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case PENDING -> EnumSet.of(CONFIRMED, CANCELLED, PAYMENT_FAILED);
            case CONFIRMED -> EnumSet.of(SHIPPING);
            case SHIPPING -> EnumSet.of(COMPLETED);
            case COMPLETED -> EnumSet.of(RETURNED);
            case CANCELLED, PAYMENT_FAILED, RETURNED -> EnumSet.noneOf(OrderStatus.class);
        };
    }
}
