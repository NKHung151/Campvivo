package vn.campvivo.promotion;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.campvivo.auth.CurrentUserId;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.order.OrderService;
import vn.campvivo.order.dto.OrderPreviewResponse;
import vn.campvivo.order.dto.OrderRequests;
import vn.campvivo.promotion.dto.AvailableVoucherResponse;
import vn.campvivo.promotion.dto.PublicVoucherResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * Voucher phía khách — api.md mục 4.5. `/validate`, `/available`: CUSTOMER; `/public`: công khai (SecurityConfig).
 * Giỏ hàng và giá lấy qua OrderService để tính đúng như lúc đặt hàng.
 */
@Validated
@RestController
@RequestMapping("/api/vouchers")
public class VoucherController {

    private final VoucherService voucherService;
    private final OrderService orderService;

    public VoucherController(VoucherService voucherService, OrderService orderService) {
        this.voucherService = voucherService;
        this.orderService = orderService;
    }

    /** Tính thử mã trên giỏ hiện tại, không trừ lượt. Vi phạm BR-5 → lỗi voucher tương ứng. */
    @PostMapping("/validate")
    public ApiResponse<ValidateResponse> validate(@CurrentUserId Long userId, @Valid @RequestBody ValidateRequest request) {
        OrderPreviewResponse preview = orderService.preview(userId,
                new OrderRequests.Preview(request.code(), request.cartItemIds(), null, null));
        VoucherService.Applied voucher = voucherService.quote(request.code(), preview.subtotal(), userId);
        return ApiResponse.of(new ValidateResponse(voucher.code(), voucher.discountType(), voucher.discountValue(),
                voucher.discount(), preview));
    }

    /** Mã công khai cho popup trang thanh toán (D-29). `cartItemIds` bỏ trống = cả giỏ. */
    @GetMapping("/available")
    public ApiResponse<List<AvailableVoucherResponse>> available(
            @CurrentUserId Long userId,
            @RequestParam(required = false) @Size(max = 100, message = "Tối đa 100 dòng") List<Long> cartItemIds) {
        return ApiResponse.of(voucherService.available(userId, orderService.checkoutSubtotal(userId, cartItemIds)));
    }

    /** Mã công khai đang có cho PromoBar (D-29). */
    @GetMapping("/public")
    public ApiResponse<List<PublicVoucherResponse>> publicList() {
        return ApiResponse.of(voucherService.publicList());
    }

    public record ValidateRequest(
            @NotBlank(message = "Vui lòng nhập mã giảm giá") @Size(max = 50, message = "Mã giảm giá tối đa 50 ký tự")
            String code,
            @Size(max = 100, message = "Tối đa 100 dòng") List<Long> cartItemIds
    ) {}

    /** `{code, discountType, discountValue, discountAmount, ...OrderPreview}`. */
    public record ValidateResponse(String code, DiscountType discountType, BigDecimal discountValue,
                                   BigDecimal discountAmount, @JsonUnwrapped OrderPreviewResponse preview) {}
}
