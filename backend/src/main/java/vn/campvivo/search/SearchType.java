package vn.campvivo.search;

/** Loại kết quả của `GET /search` (api.md mục 4.3); giá trị trên query viết HOA: `?type=PRODUCT` (sai giá trị → 400). */
public enum SearchType {
    PRODUCT,
    COMBO,
    ARTICLE
}
