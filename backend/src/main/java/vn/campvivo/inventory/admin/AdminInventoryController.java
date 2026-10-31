package vn.campvivo.inventory.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.campvivo.auth.CurrentUserId;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.inventory.InventoryReason;
import vn.campvivo.inventory.admin.AdminInventoryResponses.Item;
import vn.campvivo.inventory.admin.AdminInventoryResponses.ItemType;
import vn.campvivo.inventory.admin.AdminInventoryResponses.LogEntry;

import java.time.LocalDate;

/** API trang Tồn kho (FR-08) — docs/02-ky-thuat/api.md mục 4.10. ADMIN và EMPLOYEE. */
@RestController
@RequestMapping("/api/admin/inventory")
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
@Validated
public class AdminInventoryController {

    private final AdminInventoryService service;

    public AdminInventoryController(AdminInventoryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<Item> list(
            @RequestParam(defaultValue = "PRODUCT") ItemType type,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean lowStock,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Số dòng mỗi trang từ 1 đến 100.")
            @Max(value = 100, message = "Số dòng mỗi trang từ 1 đến 100.") int limit) {
        return service.list(type, q, lowStock, page, limit);
    }

    /** Nhập / xuất / điều chỉnh tay → dòng nhật ký vừa ghi. */
    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LogEntry> adjust(@Valid @RequestBody AdjustmentRequest request, @CurrentUserId Long userId) {
        return ApiResponse.of(service.adjust(request, userId));
    }

    @GetMapping("/logs")
    public PageResponse<LogEntry> logs(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long comboId,
            @RequestParam(required = false) ItemType type,
            @RequestParam(required = false) InventoryReason reason,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Số dòng mỗi trang từ 1 đến 100.")
            @Max(value = 100, message = "Số dòng mỗi trang từ 1 đến 100.") int limit) {
        return service.logs(productId, comboId, type, reason, from, to, page, limit);
    }
}
