package vn.campvivo.analytics;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.campvivo.analytics.AnalyticsResponses.RevenuePoint;
import vn.campvivo.analytics.AnalyticsResponses.Summary;
import vn.campvivo.analytics.AnalyticsResponses.TopItem;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.common.dto.ApiResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dashboard (FR-24) — docs/02-ky-thuat/api.md mục 4.12. Chỉ ADMIN. `from` / `to` dạng yyyy-MM-dd, gồm cả hai đầu. */
@RestController
@RequestMapping("/api/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiResponse<Summary> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.of(service.summary(from, to));
    }

    /** `groupBy` = day | month | year (mặc định day). */
    @GetMapping("/revenue")
    public ApiResponse<List<RevenuePoint>> revenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") String groupBy) {
        return ApiResponse.of(service.revenue(from, to, groupBy(groupBy)));
    }

    @GetMapping("/top-items")
    public ApiResponse<List<TopItem>> topItems(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "Số mặt hàng từ 1 đến 50.")
            @Max(value = 50, message = "Số mặt hàng từ 1 đến 50.") int limit) {
        return ApiResponse.of(service.topItems(from, to, limit));
    }

    private static AnalyticsService.GroupBy groupBy(String raw) {
        try {
            return AnalyticsService.GroupBy.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                    Map.of("fieldErrors", Map.of("groupBy", "Chỉ nhận day, month hoặc year.")));
        }
    }
}
