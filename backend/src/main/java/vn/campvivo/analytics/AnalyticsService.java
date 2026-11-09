package vn.campvivo.analytics;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.analytics.AnalyticsResponses.RevenuePoint;
import vn.campvivo.analytics.AnalyticsResponses.Summary;
import vn.campvivo.analytics.AnalyticsResponses.TopItem;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard (FR-24). Doanh thu = tổng `final_amount` của đơn COMPLETED theo `completed_at` (RETURNED không tính); ngày tính
 * theo giờ Việt Nam. Khoảng ngày [from, to] gồm cả hai đầu; bỏ trống = 30 ngày gần nhất.
 */
@Service
public class AnalyticsService {

    static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    static final List<String> STATUSES =
            List.of("PENDING", "CONFIRMED", "SHIPPING", "COMPLETED", "CANCELLED", "PAYMENT_FAILED", "RETURNED");
    /** Nhóm theo ngày tối đa ~1 năm (366 mốc), theo tháng tối đa 10 năm — tránh biểu đồ quá dày. */
    private static final long MAX_DAYS_BY_DAY = 366;
    private static final long MAX_YEARS = 10;

    public enum GroupBy { DAY, MONTH, YEAR }

    /** Khoảng ngày đã kiểm tra + mốc thời gian [start, end) theo giờ VN. */
    record Range(LocalDate from, LocalDate to, OffsetDateTime start, OffsetDateTime end) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public AnalyticsService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Summary summary(LocalDate from, LocalDate to) {
        Range r = range(from, to);
        MapSqlParameterSource p = params(r);
        Map<String, Object> revenue = jdbc.queryForMap("""
                SELECT COALESCE(sum(final_amount), 0) AS revenue, count(*) AS orders
                FROM orders WHERE status = 'COMPLETED' AND completed_at >= :start AND completed_at < :end
                """, p);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        STATUSES.forEach(s -> byStatus.put(s, 0L));
        jdbc.query("""
                SELECT status, count(*) AS n FROM orders WHERE created_at >= :start AND created_at < :end GROUP BY status
                """, p, rs -> {
            byStatus.put(rs.getString("status"), rs.getLong("n"));
        });
        Long newCustomers = jdbc.queryForObject("""
                SELECT count(*) FROM users WHERE role = 'CUSTOMER' AND created_at >= :start AND created_at < :end
                """, p, Long.class);
        return new Summary(r.from(), r.to(), (BigDecimal) revenue.get("revenue"), ((Number) revenue.get("orders")).longValue(),
                byStatus, newCustomers == null ? 0 : newCustomers);
    }

    /** Doanh thu + số đơn hoàn tất theo ngày / tháng / năm; đủ mọi mốc trong khoảng (mốc trống = 0). */
    @Transactional(readOnly = true)
    public List<RevenuePoint> revenue(LocalDate from, LocalDate to, GroupBy groupBy) {
        Range r = range(from, to);
        if (groupBy == GroupBy.DAY && ChronoUnit.DAYS.between(r.from(), r.to()) + 1 > MAX_DAYS_BY_DAY) {
            throw fieldError("groupBy", "Khoảng quá dài để xem theo ngày (tối đa 366 ngày) — hãy chọn theo tháng.");
        }
        String unit = groupBy.name().toLowerCase();
        String format = switch (groupBy) {
            case DAY -> "YYYY-MM-DD";
            case MONTH -> "YYYY-MM";
            case YEAR -> "YYYY";
        };
        Map<String, RevenuePoint> found = new HashMap<>();
        jdbc.query("""
                SELECT to_char(date_trunc('%s', completed_at AT TIME ZONE 'Asia/Ho_Chi_Minh'), '%s') AS period,
                       sum(final_amount) AS revenue, count(*) AS orders
                FROM orders WHERE status = 'COMPLETED' AND completed_at >= :start AND completed_at < :end
                GROUP BY 1
                """.formatted(unit, format), params(r), rs -> {
            String period = rs.getString("period");
            found.put(period, new RevenuePoint(period, rs.getBigDecimal("revenue"), rs.getLong("orders")));
        });
        return periods(r, groupBy).stream()
                .map(k -> found.getOrDefault(k, new RevenuePoint(k, BigDecimal.ZERO, 0)))
                .toList();
    }

    /** Top mặt hàng (sản phẩm hoặc combo) theo số lượng bán trong đơn COMPLETED; hoà thì doanh thu cao hơn trước. */
    @Transactional(readOnly = true)
    public List<TopItem> topItems(LocalDate from, LocalDate to, int limit) {
        Range r = range(from, to);
        MapSqlParameterSource p = params(r).addValue("limit", limit);
        return jdbc.query("""
                SELECT CASE WHEN oi.product_id IS NOT NULL THEN 'PRODUCT' ELSE 'COMBO' END AS type,
                       COALESCE(oi.product_id, oi.combo_id) AS id,
                       (array_agg(oi.item_name ORDER BY o.completed_at DESC))[1] AS name,
                       (array_agg(oi.item_image_url ORDER BY o.completed_at DESC))[1] AS image_url,
                       sum(oi.quantity) AS quantity, sum(oi.line_total) AS revenue
                FROM order_items oi JOIN orders o ON o.id = oi.order_id
                WHERE o.status = 'COMPLETED' AND o.completed_at >= :start AND o.completed_at < :end
                GROUP BY 1, 2
                ORDER BY quantity DESC, revenue DESC, id
                LIMIT :limit
                """, p, (rs, i) -> new TopItem(rs.getString("type"), rs.getLong("id"), rs.getString("name"),
                rs.getString("image_url"), rs.getLong("quantity"), rs.getBigDecimal("revenue")));
    }

    // ---------------------------------------------------------------------------------------------

    private Range range(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock.withZone(VN));
        LocalDate t = to == null ? today : to;
        LocalDate f = from == null ? t.minusDays(29) : from;
        if (f.isAfter(t)) {
            throw fieldError("from", "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.");
        }
        if (f.isBefore(t.minusYears(MAX_YEARS))) {
            throw fieldError("from", "Khoảng thời gian tối đa 10 năm.");
        }
        return new Range(f, t, f.atStartOfDay(VN).toOffsetDateTime(), t.plusDays(1).atStartOfDay(VN).toOffsetDateTime());
    }

    private static MapSqlParameterSource params(Range r) {
        return new MapSqlParameterSource().addValue("start", r.start()).addValue("end", r.end());
    }

    /** Mọi mốc trong khoảng, đúng định dạng `period` của SQL. */
    private static List<String> periods(Range r, GroupBy groupBy) {
        return switch (groupBy) {
            case DAY -> r.from().datesUntil(r.to().plusDays(1)).map(LocalDate::toString).toList();
            case MONTH -> r.from().withDayOfMonth(1).datesUntil(r.to().plusDays(1), java.time.Period.ofMonths(1))
                    .map(d -> d.toString().substring(0, 7)).toList();
            case YEAR -> java.util.stream.IntStream.rangeClosed(r.from().getYear(), r.to().getYear())
                    .mapToObj(Integer::toString).toList();
        };
    }

    private static BusinessException fieldError(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                Map.of("fieldErrors", Map.of(field, message)));
    }
}
