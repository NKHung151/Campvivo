package vn.campvivo.combo.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Body POST /api/admin/combos/{id}/pack | unpack — số bộ đóng gói/tháo (D-22). */
public record PackRequest(
        @Min(value = 1, message = "Số bộ từ 1 đến 999.") @Max(value = 999, message = "Số bộ từ 1 đến 999.") int sets
) {
}
