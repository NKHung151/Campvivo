package vn.campvivo.cart;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.campvivo.auth.CurrentUserId;
import vn.campvivo.cart.dto.CartRequests;
import vn.campvivo.cart.dto.CartResponse;
import vn.campvivo.common.dto.ApiResponse;

/** Giỏ hàng của khách đang đăng nhập — api.md mục 4.4 (quyền CUSTOMER, SecurityConfig). */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<CartResponse> get(@CurrentUserId Long userId) {
        return ApiResponse.of(cartService.getCart(userId));
    }

    @PostMapping("/items")
    public ApiResponse<CartResponse> add(@CurrentUserId Long userId, @Valid @RequestBody CartRequests.AddItem request) {
        return ApiResponse.of(cartService.addItem(userId, request));
    }

    @PatchMapping("/items/{id}")
    public ApiResponse<CartResponse> update(@CurrentUserId Long userId, @PathVariable Long id,
                                            @Valid @RequestBody CartRequests.UpdateItem request) {
        return ApiResponse.of(cartService.updateItem(userId, id, request.quantity()));
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<CartResponse> remove(@CurrentUserId Long userId, @PathVariable Long id) {
        return ApiResponse.of(cartService.removeItem(userId, id));
    }
}
