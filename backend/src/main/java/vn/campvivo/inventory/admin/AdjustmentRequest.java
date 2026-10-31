package vn.campvivo.inventory.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.campvivo.inventory.InventoryReason;

/**
 * Body POST /api/admin/inventory/adjustments — docs/02-ky-thuat/api.md mục 4.10. Đúng một trong productId / comboId.
 * `changeQuantity`: dương = nhập, âm = xuất. IMPORT phải dương, EXPORT phải âm, ADJUST tuỳ dấu; RETURN không chọn tay.
 * Combo chỉ được giảm (D-27). Ràng buộc chéo kiểm tra ở AdminInventoryService.
 */
public record AdjustmentRequest(
        Long productId,
        Long comboId,

        @NotNull(message = "Vui lòng nhập số lượng.")
        @Min(value = -100_000, message = "Số lượng từ -100.000 đến 100.000.")
        @Max(value = 100_000, message = "Số lượng từ -100.000 đến 100.000.")
        Integer changeQuantity,

        @NotNull(message = "Vui lòng chọn lý do.")
        InventoryReason reason,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự.")
        String note
) {
}
