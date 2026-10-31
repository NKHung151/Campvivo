package vn.campvivo.shipping;

import java.util.List;

/**
 * Tạo vận đơn GHN. `clientOrderCode` = mã đơn Campvivo (orders.order_code) để GHN chặn tạo trùng.
 * `codAmount` = số tiền GHN thu hộ (COD: tổng đơn; đã thanh toán VNPay: 0). Cân nặng tính bằng gram.
 */
public record GhnCreateOrderRequest(
        String clientOrderCode,
        String toName,
        String toPhone,
        String toAddress,
        String toWardCode,
        int toDistrictId,
        long codAmount,
        long insuranceValue,
        int weight,
        String note,
        List<Item> items
) {
    public record Item(String name, String code, int quantity, long price, int weight) {}
}
