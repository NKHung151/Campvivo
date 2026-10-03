package vn.campvivo.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Khớp request POST /orders trong docs/02-ky-thuat/api.md. */
public record CreateOrderRequest(
        @NotNull Long cartId,
        @NotNull Long shippingAddressId,
        @NotBlank String paymentMethod,
        String promotionCode // nullable — không bắt buộc
) {
}
