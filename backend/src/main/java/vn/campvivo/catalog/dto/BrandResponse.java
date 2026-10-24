package vn.campvivo.catalog.dto;

/** Một thương hiệu đang có sản phẩm bán — GET /brands (docs/02-ky-thuat/api.md mục 4.3). */
public record BrandResponse(String name, long productCount) {}
