package vn.campvivo.order;

import java.math.BigDecimal;

/** Thống kê đơn của một khách (xem {@link OrderQueryService#statsForUser}). `totalSpent` tính theo đơn COMPLETED, đơn vị VND. */
public record UserOrderStats(long orderCount, long completedOrderCount, BigDecimal totalSpent) {
}
