package vn.campvivo.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import vn.campvivo.payment.PaymentMethod;

import java.util.List;

/** Body API đặt hàng — api.md mục 4.5. `cartItemIds` rỗng/null = đặt cả giỏ. */
public final class OrderRequests {

    private OrderRequests() {
    }

    /** POST /orders/preview — có địa chỉ GHN thì tính luôn phí ship (D-27). */
    public record Preview(
            @Size(max = 50, message = "Mã giảm giá tối đa 50 ký tự") String voucherCode,
            @Size(max = 100, message = "Tối đa 100 dòng") List<Long> cartItemIds,
            @Positive(message = "Quận/huyện không hợp lệ") Integer shippingDistrictId,
            @Size(max = 20, message = "Phường/xã không hợp lệ") String shippingWardCode
    ) {}

    /** POST /orders. */
    public record Create(
            @NotBlank(message = "Vui lòng nhập họ tên người nhận") @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
            String recipientName,
            @NotBlank(message = "Vui lòng nhập số điện thoại")
            @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải gồm 10 số, bắt đầu bằng 0")
            String recipientPhone,
            @NotBlank(message = "Vui lòng nhập địa chỉ nhận hàng") @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
            String shippingAddress,
            @NotNull(message = "Vui lòng chọn quận/huyện") @Positive(message = "Quận/huyện không hợp lệ")
            Integer shippingDistrictId,
            @NotBlank(message = "Vui lòng chọn phường/xã") @Size(max = 20, message = "Phường/xã không hợp lệ")
            String shippingWardCode,
            boolean saveAsDefault,
            @NotNull(message = "Vui lòng chọn phương thức thanh toán") PaymentMethod paymentMethod,
            @Size(max = 50, message = "Mã giảm giá tối đa 50 ký tự") String voucherCode,
            @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note,
            @NotNull(message = "Thiếu tổng tiền") @Min(value = 0, message = "Tổng tiền không hợp lệ") Long expectedTotal,
            @Size(max = 100, message = "Tối đa 100 dòng") List<Long> cartItemIds
    ) {}
}
