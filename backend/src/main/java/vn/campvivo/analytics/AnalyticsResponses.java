package vn.campvivo.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Response dashboard (FR-24) — khớp docs/02-ky-thuat/api.md mục 4.12 và frontend/src/types/api/analytics.ts. */
public final class AnalyticsResponses {

    private AnalyticsResponses() {}

    /**
     * @param revenue         tổng `final_amount` đơn COMPLETED hoàn tất trong khoảng (theo `completed_at`)
     * @param completedOrders số đơn đó
     * @param ordersByStatus  số đơn đặt trong khoảng (theo `created_at`) theo từng trạng thái — đủ 7 trạng thái, 0 nếu không có
     * @param newCustomers    tài khoản khách hàng tạo trong khoảng
     */
    public record Summary(LocalDate from, LocalDate to, BigDecimal revenue, long completedOrders,
                          Map<String, Long> ordersByStatus, long newCustomers) {}

    /** Một mốc của biểu đồ; `period` = "2026-10-17" | "2026-10" | "2026". Mốc không có đơn vẫn có (0). */
    public record RevenuePoint(String period, BigDecimal revenue, long orderCount) {}

    /** Mặt hàng bán chạy (đơn COMPLETED); `name` / `imageUrl` = bản chụp mới nhất trong dòng đơn. */
    public record TopItem(String type, Long id, String name, String imageUrl, long quantitySold, BigDecimal revenue) {}
}
