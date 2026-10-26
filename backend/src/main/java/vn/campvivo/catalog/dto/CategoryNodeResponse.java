package vn.campvivo.catalog.dto;

import java.util.List;

/** Nút cây danh mục — GET /categories (docs/02-ky-thuat/api.md mục 4.3, kiểu `CategoryNode`). */
public record CategoryNodeResponse(Long id, String name, String slug, List<CategoryNodeResponse> children) {}
