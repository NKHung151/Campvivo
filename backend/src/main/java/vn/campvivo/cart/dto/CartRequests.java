package vn.campvivo.cart.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Body của API giỏ hàng — docs/02-ky-thuat/api.md mục 4.4. */
public final class CartRequests {

    private CartRequests() {
    }

    /** POST /cart/items — đúng một trong productId, comboId. */
    public record AddItem(
            Long productId,
            Long comboId,
            @NotNull(message = "Vui lòng nhập số lượng")
            @Min(value = 1, message = "Số lượng phải từ 1") @Max(value = 999, message = "Số lượng tối đa 999")
            Integer quantity
    ) {
        @AssertTrue(message = "Chọn đúng một sản phẩm hoặc một combo")
        public boolean isExactlyOneItem() {
            return (productId == null) != (comboId == null);
        }
    }

    /** PATCH /cart/items/{id}. */
    public record UpdateItem(
            @NotNull(message = "Vui lòng nhập số lượng")
            @Min(value = 1, message = "Số lượng phải từ 1") @Max(value = 999, message = "Số lượng tối đa 999")
            Integer quantity
    ) {}
}
