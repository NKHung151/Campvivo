package vn.campvivo.inventory;

/** Một dòng tồn kho cần giữ chỗ / trừ / trả: đúng một trong `productId`, `comboId` khác null. */
public record StockLine(Long productId, Long comboId, int quantity) {

    public static StockLine product(long productId, int quantity) {
        return new StockLine(productId, null, quantity);
    }

    public static StockLine combo(long comboId, int quantity) {
        return new StockLine(null, comboId, quantity);
    }

    public boolean isProduct() {
        return productId != null;
    }
}
