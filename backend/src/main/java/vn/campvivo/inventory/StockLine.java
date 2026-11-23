package vn.campvivo.inventory;

/**
 * Một dòng tồn kho cần giữ chỗ / trừ / trả: đúng một trong `productId`, `comboId` khác null. `variantId` = biến thể của
 * sản phẩm (D-40); null với combo và với dòng đơn cũ chưa lưu biến thể (chỉ đổi tổng sản phẩm).
 */
public record StockLine(Long productId, Long variantId, Long comboId, int quantity) {

    public static StockLine product(long productId, Long variantId, int quantity) {
        return new StockLine(productId, variantId, null, quantity);
    }

    public static StockLine combo(long comboId, int quantity) {
        return new StockLine(null, null, comboId, quantity);
    }

    public boolean isProduct() {
        return productId != null;
    }
}
