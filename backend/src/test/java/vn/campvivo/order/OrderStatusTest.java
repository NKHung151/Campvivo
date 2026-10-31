package vn.campvivo.order;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.campvivo.order.OrderStatus.CANCELLED;
import static vn.campvivo.order.OrderStatus.COMPLETED;
import static vn.campvivo.order.OrderStatus.CONFIRMED;
import static vn.campvivo.order.OrderStatus.PAYMENT_FAILED;
import static vn.campvivo.order.OrderStatus.PENDING;
import static vn.campvivo.order.OrderStatus.RETURNED;
import static vn.campvivo.order.OrderStatus.SHIPPING;

/** Máy trạng thái database.md mục 5.1 — kiểm đủ mọi cặp (hợp lệ / không hợp lệ). */
class OrderStatusTest {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(CONFIRMED, CANCELLED, PAYMENT_FAILED),
            CONFIRMED, EnumSet.of(SHIPPING),
            SHIPPING, EnumSet.of(COMPLETED),
            COMPLETED, EnumSet.of(RETURNED),
            CANCELLED, EnumSet.noneOf(OrderStatus.class),
            PAYMENT_FAILED, EnumSet.noneOf(OrderStatus.class),
            RETURNED, EnumSet.noneOf(OrderStatus.class));

    @Test
    void everyPairMatchesSpec() {
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                assertThat(from.canTransitionTo(to)).as(from + " → " + to).isEqualTo(ALLOWED.get(from).contains(to));
            }
        }
    }
}
