package vn.campvivo.order.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.campvivo.auth.CurrentUserId;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.order.OrderStatus;
import vn.campvivo.order.admin.AdminOrderResponses.Detail;
import vn.campvivo.order.admin.AdminOrderResponses.StatusChange;
import vn.campvivo.order.admin.AdminOrderResponses.StatusRequest;
import vn.campvivo.order.admin.AdminOrderResponses.Summary;
import vn.campvivo.order.admin.AdminOrderResponses.Tracking;
import vn.campvivo.payment.PaymentMethod;

import java.time.LocalDate;

/** Quản lý đơn hàng — api.md mục 4.10 (quyền Staff: EMPLOYEE hoặc ADMIN, SecurityConfig `/api/admin/**`). */
@Validated
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderService service;

    public AdminOrderController(AdminOrderService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<Summary> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Số dòng mỗi trang từ 1 đến 100.")
            @Max(value = 100, message = "Số dòng mỗi trang từ 1 đến 100.") int limit) {
        return service.list(new AdminOrderResponses.Query(status, paymentMethod, q, from, to, page, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<Detail> get(@PathVariable Long id) {
        return ApiResponse.of(service.detail(id));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<StatusChange> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request,
                                                  @CurrentUserId Long staffId) {
        return ApiResponse.of(service.changeStatus(id, request, staffId));
    }

    @PostMapping("/{id}/shipment")
    public ApiResponse<Detail> createShipment(@PathVariable Long id) {
        return ApiResponse.of(service.createShipment(id));
    }

    @GetMapping("/{id}/shipment")
    public ApiResponse<Tracking> tracking(@PathVariable Long id) {
        return ApiResponse.of(service.tracking(id));
    }
}
