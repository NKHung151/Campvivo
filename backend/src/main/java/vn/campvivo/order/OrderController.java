package vn.campvivo.order;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.campvivo.auth.CurrentUserId;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.order.dto.CreateOrderResponse;
import vn.campvivo.order.dto.OrderDetailResponse;
import vn.campvivo.order.dto.OrderPreviewResponse;
import vn.campvivo.order.dto.OrderRequests;
import vn.campvivo.order.dto.OrderSummaryResponse;

/** Đặt hàng của khách — api.md mục 4.5 (quyền CUSTOMER, SecurityConfig). */
@Validated
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderQueryService orderQueryService;

    public OrderController(OrderService orderService, OrderQueryService orderQueryService) {
        this.orderService = orderService;
        this.orderQueryService = orderQueryService;
    }

    @PostMapping("/preview")
    public ApiResponse<OrderPreviewResponse> preview(@CurrentUserId Long userId,
                                                     @Valid @RequestBody OrderRequests.Preview request) {
        return ApiResponse.of(orderService.preview(userId, request));
    }

    /**
     * IP khách gửi VNPay (vnp_IpAddr). Sau reverse proxy, server.forward-headers-strategy=framework đã thay remoteAddr
     * bằng IP thật từ X-Forwarded-For.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateOrderResponse> create(@CurrentUserId Long userId,
                                                   @Valid @RequestBody OrderRequests.Create request,
                                                   HttpServletRequest http) {
        return ApiResponse.of(orderService.create(userId, request, http.getRemoteAddr()));
    }

    @GetMapping
    public PageResponse<OrderSummaryResponse> list(
            @CurrentUserId Long userId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "Số dòng mỗi trang từ 1 đến 100.")
            @Max(value = 100, message = "Số dòng mỗi trang từ 1 đến 100.") int limit) {
        return orderQueryService.listForCustomer(userId, status, page, limit);
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDetailResponse> detail(@CurrentUserId Long userId, @PathVariable Long id) {
        return ApiResponse.of(orderQueryService.getForCustomer(userId, id));
    }

    /** Body `{reason?}` (≤ 500 ký tự) — huỷ khi PENDING. */
    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderDetailResponse> cancel(@CurrentUserId Long userId, @PathVariable Long id,
                                                   @Valid @RequestBody(required = false) CancelRequest request) {
        orderService.cancelByCustomer(userId, id, request == null ? null : request.reason());
        return ApiResponse.of(orderQueryService.getForCustomer(userId, id));
    }

    public record CancelRequest(@Size(max = 500, message = "Lý do tối đa 500 ký tự") String reason) {}
}
