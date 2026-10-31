package vn.campvivo.shipping.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Tính thử phí ship ở trang thanh toán. `quantity` = tổng số món (cân nặng = món × app.ghn.default-weight),
 * `orderValue` = tạm tính sau giảm giá (để xét miễn phí ship, D-27). Tạo đơn thì server tự tính lại từ giỏ.
 */
public record ShippingFeeRequest(
        @NotNull(message = "Vui lòng chọn quận/huyện") @Positive(message = "Quận/huyện không hợp lệ")
        Integer toDistrictId,
        @NotBlank(message = "Vui lòng chọn phường/xã") @Size(max = 20, message = "Phường/xã không hợp lệ")
        String toWardCode,
        @NotNull(message = "Thiếu số lượng sản phẩm") @Min(value = 1, message = "Số lượng phải từ 1")
        @Max(value = 999, message = "Số lượng tối đa 999")
        Integer quantity,
        @Min(value = 0, message = "Giá trị đơn không hợp lệ")
        Long orderValue
) {}
