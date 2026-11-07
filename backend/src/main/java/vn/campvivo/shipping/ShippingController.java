package vn.campvivo.shipping;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.shipping.dto.DistrictResponse;
import vn.campvivo.shipping.dto.ProvinceResponse;
import vn.campvivo.shipping.dto.ShippingFeeRequest;
import vn.campvivo.shipping.dto.ShippingFeeResponse;
import vn.campvivo.shipping.dto.ShippingServiceResponse;
import vn.campvivo.shipping.dto.WardResponse;

import java.util.List;

/** API vận chuyển công khai — docs/02-ky-thuat/api.md mục 4.12 (địa chỉ 3 cấp GHN, gói dịch vụ, tính thử phí ship). */
@Validated
@RestController
@RequestMapping("/api/shipping")
public class ShippingController {

    private final GhnService ghnService;
    private final ShippingService shippingService;

    public ShippingController(GhnService ghnService, ShippingService shippingService) {
        this.ghnService = ghnService;
        this.shippingService = shippingService;
    }

    @GetMapping("/provinces")
    public ApiResponse<List<ProvinceResponse>> provinces() {
        return ApiResponse.of(ghnService.getProvinces());
    }

    @GetMapping("/districts")
    public ApiResponse<List<DistrictResponse>> districts(
            @RequestParam @Positive(message = "Tỉnh/thành không hợp lệ") int provinceId) {
        return ApiResponse.of(ghnService.getDistricts(provinceId));
    }

    @GetMapping("/wards")
    public ApiResponse<List<WardResponse>> wards(
            @RequestParam @Positive(message = "Quận/huyện không hợp lệ") int districtId) {
        return ApiResponse.of(ghnService.getWards(districtId));
    }

    @GetMapping("/services")
    public ApiResponse<List<ShippingServiceResponse>> services(
            @RequestParam @Positive(message = "Quận/huyện không hợp lệ") int toDistrictId) {
        return ApiResponse.of(ghnService.getAvailableServices(toDistrictId));
    }

    @PostMapping("/fee")
    public ApiResponse<ShippingFeeResponse> fee(@Valid @RequestBody ShippingFeeRequest request) {
        long orderValue = request.orderValue() == null ? 0 : request.orderValue();
        return ApiResponse.of(shippingService.quote(
                request.toDistrictId(), request.toWardCode().trim(), request.quantity(), orderValue));
    }
}
