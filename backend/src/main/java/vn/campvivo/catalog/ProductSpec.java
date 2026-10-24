package vn.campvivo.catalog;

/** Một dòng thông số sản phẩm, vd. {"name": "Chất liệu", "value": "Nhôm nguyên khối"} (cột products.specs). */
public record ProductSpec(String name, String value) {
}
