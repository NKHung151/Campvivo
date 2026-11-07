package vn.campvivo.promotion.admin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import vn.campvivo.promotion.DiscountType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Tạo/sửa voucher (FR-16) — `VoucherRequest` ở api.md mục 4.10. Ràng buộc chéo (miền giá trị theo loại, `maxDiscount`
 * chỉ cho PERCENTAGE, hạn sau ngày bắt đầu, `quantity ≥ usedCount`, mã trùng) kiểm ở AdminVoucherService.
 */
public record VoucherRequest(
        /* Chữ thường được đổi IN HOA trước khi lưu. */
        @NotBlank(message = "Vui lòng nhập mã giảm giá.")
        @Pattern(regexp = "^\\s*[A-Za-z0-9_-]{3,50}\\s*$",
                message = "Mã gồm 3–50 ký tự chữ không dấu, số, dấu gạch ngang hoặc gạch dưới.")
        String code,

        @NotNull(message = "Vui lòng chọn loại giảm giá.")
        DiscountType discountType,

        @NotNull(message = "Vui lòng nhập giá trị giảm.")
        @DecimalMin(value = "1", message = "Giá trị giảm phải lớn hơn 0.")
        @DecimalMax(value = "999999999999", message = "Giá trị giảm quá lớn.")
        @Digits(integer = 12, fraction = 0, message = "Giá trị giảm phải là số nguyên.")
        BigDecimal discountValue,

        @DecimalMin(value = "1", message = "Giảm tối đa phải lớn hơn 0.")
        @DecimalMax(value = "999999999999", message = "Giảm tối đa quá lớn.")
        @Digits(integer = 12, fraction = 0, message = "Giảm tối đa phải là số nguyên (VND).")
        BigDecimal maxDiscount,

        @NotNull(message = "Vui lòng nhập giá trị đơn tối thiểu.")
        @DecimalMin(value = "0", message = "Đơn tối thiểu không được âm.")
        @DecimalMax(value = "999999999999", message = "Đơn tối thiểu quá lớn.")
        @Digits(integer = 12, fraction = 0, message = "Đơn tối thiểu phải là số nguyên (VND).")
        BigDecimal minOrderValue,

        @Min(value = 1, message = "Số lượng phát hành phải từ 1.")
        @Max(value = 1_000_000, message = "Số lượng phát hành tối đa 1.000.000.")
        int quantity,

        @NotNull(message = "Vui lòng chọn ngày bắt đầu.")
        OffsetDateTime startDate,

        @NotNull(message = "Vui lòng chọn ngày hết hạn.")
        OffsetDateTime expiryDate,

        boolean isActive,

        boolean isPublic
) {}
