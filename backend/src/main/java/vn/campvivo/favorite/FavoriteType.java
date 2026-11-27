package vn.campvivo.favorite;

/** Loại đối tượng được yêu thích (D-06) — mỗi loại một cột FK trong `favorites`. */
public enum FavoriteType {
    PRODUCT("product_id"),
    COMBO("combo_id"),
    ARTICLE("article_id");

    private final String column;

    FavoriteType(String column) {
        this.column = column;
    }

    /** Tên cột trong `favorites` (hằng số trong mã — an toàn để ghép vào SQL). */
    String column() {
        return column;
    }
}
