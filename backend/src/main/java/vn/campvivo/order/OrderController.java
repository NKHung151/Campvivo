package vn.campvivo.order;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.campvivo.common.dto.ApiResponse;

/**
 * Controller mỏng — không chứa nghiệp vụ, chỉ map request/response.
 * Endpoint khớp docs/02-ky-thuat/api.md (POST /orders).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<OrderResponse> create(@AuthenticationPrincipal Long userId,
                                              @Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(userId, request);
        // TODO: thay null bằng paymentUrl thật khi PaymentService được code (Người 1).
        return ApiResponse.of(OrderResponse.of(order, null));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        orderService.cancelOrder(id, userId);
        return ApiResponse.of(null);
    }
}
