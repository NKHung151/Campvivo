/** Kiểu module catalog (danh mục, sản phẩm, thương hiệu, tìm kiếm) — khớp docs/02-ky-thuat/api.md mục 3, 4.3, 4.10. */
import type { ArticleCategory, ItemType, Money, PageQuery } from "./common";

/* ───────────── Public ───────────── */

export interface CategoryNode {
  id: number;
  name: string;
  slug: string;
  children: CategoryNode[];
}

/** Sản phẩm hoặc combo trong danh sách. `price` = giá sau chiến dịch (BR-6). */
export interface ItemSummary {
  type: ItemType;
  id: number;
  name: string;
  slug: string;
  primaryImageUrl: string | null;
  originalPrice: Money;
  price: Money;
  availableQuantity: number;
  averageRating: number | null;
  reviewCount: number;
  /** Chỉ có với PRODUCT. */
  brand?: string | null;
}

export interface CategoryRef {
  id: number;
  name: string;
  slug: string;
  /** Đường dẫn từ danh mục gốc tới danh mục này (breadcrumb). */
  path: { name: string; slug: string }[];
}

export interface ImageRef {
  url: string;
  isPrimary: boolean;
}

/** Tuỳ chọn của sản phẩm (D-37), vd. { name: "Màu sắc", values: ["Tím", "Xanh"] }. Tối đa 2. */
export interface ProductOption {
  name: string;
  values: string[];
}

/** Biến thể đang bán — cùng giá với sản phẩm. `values` theo thứ tự `options`; rỗng với biến thể mặc định. */
export interface ProductVariant {
  id: number;
  /** "Tím / S/M"; null = sản phẩm không có tuỳ chọn. */
  title: string | null;
  values: string[];
  availableQuantity: number;
}

export interface ProductDetail extends ItemSummary {
  /** Rỗng = không có tuỳ chọn (`variants` có đúng 1 biến thể mặc định). */
  options: ProductOption[];
  variants: ProductVariant[];
  shortDescription: string | null;
  description: string | null;
  brand: string | null;
  specs: ProductSpec[];
  category: CategoryRef;
  images: ImageRef[];
  campaign: { name: string; slug: string; endDate: string } | null;
  isFavorite?: boolean;
}

/** Sản phẩm bán chạy của một danh mục cấp 2 (GET /products/best-sellers — D-38), bán chạy nhất trước. */
export interface CategoryBestSellers {
  categoryId: number;
  items: ItemSummary[];
}

export interface Brand {
  name: string;
  productCount: number;
}

export type ProductSort = "newest" | "price_asc" | "price_desc" | "name_asc";

export interface ProductQuery extends PageQuery {
  /** Slug danh mục — gồm cả danh mục con. */
  category?: string;
  brand?: string;
  /** Tên, thương hiệu, slug — gõ không dấu được. */
  q?: string;
  /** Lấy đúng các sản phẩm theo slug (tối đa 100). */
  slugs?: string[];
  minPrice?: Money;
  maxPrice?: Money;
  /** Chỉ sản phẩm đang được giảm (giá sau chiến dịch < giá gốc). */
  onSale?: boolean;
  sortBy?: ProductSort;
}

/** Combo đang bán (FR-07). `itemsTotal` = tổng giá lẻ hiện tại của các món — hiện mức tiết kiệm. */
export interface ComboSummary extends ItemSummary {
  itemsTotal: Money;
  itemCount: number;
  /** Danh mục cấp 1 của combo. */
  category: { id: number; name: string; slug: string };
}

/** Món trong combo. */
export interface ComboDetailItem {
  product: ItemSummary;
  /** Biến thể trong combo, vd. "Tím / S/M"; null = sản phẩm không có tuỳ chọn. */
  variantTitle: string | null;
  quantity: number;
}

export interface ComboDetail extends ItemSummary {
  itemsTotal: Money;
  description: string | null;
  category: { id: number; name: string; slug: string };
  images: ImageRef[];
  items: ComboDetailItem[];
}

export type ComboSort = "newest" | "price_asc" | "price_desc";

export interface ComboQuery extends PageQuery {
  /** Slug danh mục cấp 1 (D-23). */
  category?: string;
  q?: string;
  sortBy?: ComboSort;
}

export interface ArticleSummary {
  id: number;
  title: string;
  slug: string;
  summary: string | null;
  thumbnailUrl: string | null;
  category: ArticleCategory;
  publishedAt: string;
}

export interface SearchResult {
  products: ItemSummary[];
  combos: ItemSummary[];
  articles: ArticleSummary[];
}

/* ───────────── Admin ───────────── */

/** Cây danh mục admin: kèm số sản phẩm/combo để biết có xoá được không (BR-13). */
export interface AdminCategoryNode {
  id: number;
  name: string;
  slug: string;
  parentId: number | null;
  /** Sản phẩm / combo gắn trực tiếp (không gồm danh mục con) — khác 0 thì không xoá được. */
  productCount: number;
  comboCount: number;
  children: AdminCategoryNode[];
}

export interface CategoryRequest {
  name: string;
  /** Bỏ trống → tạo mới thì sinh từ tên, sửa thì giữ slug cũ. */
  slug?: string;
  /** null = danh mục gốc. */
  parentId?: number | null;
}

export interface AdminProductSummary {
  id: number;
  name: string;
  slug: string;
  brand: string | null;
  categoryId: number;
  categoryName: string;
  /** Tên danh mục từ gốc tới danh mục của sản phẩm, vd. ["Đạp Xe", "Xe đạp"]. */
  categoryPath: string[];
  price: Money;
  stockQuantity: number;
  reservedQuantity: number;
  /** Khả dụng = tổng (stock − reserved) các biến thể đang bán (BR-1). */
  availableQuantity: number;
  /** Số biến thể (1 = không có tuỳ chọn). */
  variantCount: number;
  /** Tồn kho từng biến thể; rỗng khi sản phẩm không có tuỳ chọn (D-37). */
  variants: { id: number; title: string; stockQuantity: number; availableQuantity: number; isActive: boolean }[];
  /** Hàng đã bán theo đơn chưa trừ vào biến thể nào (D-37). */
  unallocatedSold: number;
  isActive: boolean;
  primaryImageUrl: string | null;
  updatedAt: string;
}

export interface AdminProductImage {
  url: string;
  isPrimary: boolean;
  displayOrder: number;
}

/** Một dòng thông số: { name: "Chất liệu", value: "Nhôm" }. */
export interface ProductSpec {
  name: string;
  value: string;
}

/** Tuỳ chọn trong admin: id giữ nguyên khi đổi tên → biến thể và tồn kho được giữ (D-37). */
export interface AdminProductOption {
  id: number;
  name: string;
  position: number;
  values: { id: number; value: string }[];
}

export interface AdminProductVariant {
  id: number;
  title: string | null;
  values: string[];
  sku: string | null;
  stockQuantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  isActive: boolean;
}

export interface AdminProductDetail extends Omit<AdminProductSummary, "categoryPath" | "variantCount" | "variants" | "unallocatedSold"> {
  shortDescription: string | null;
  description: string | null;
  specs: ProductSpec[];
  images: AdminProductImage[];
  options: AdminProductOption[];
  variants: AdminProductVariant[];
  createdAt: string;
}

/** `id` có = tuỳ chọn / giá trị đã có; bỏ trống = mới. */
export interface ProductOptionRequest {
  id?: number | null;
  name: string;
  values: { id?: number | null; value: string }[];
}

/** Thiết lập của một tổ hợp (server tự sinh mọi tổ hợp). `values` theo thứ tự `options`. */
export interface ProductVariantRequest {
  values: string[];
  sku?: string | null;
  isActive?: boolean;
}

export interface ProductRequest {
  name: string;
  slug?: string;
  shortDescription?: string | null;
  description?: string | null;
  brand?: string | null;
  price: Money;
  categoryId: number;
  isActive: boolean;
  /** Tối đa 30 dòng, tên không trùng. */
  specs: ProductSpec[];
  /** Thay toàn bộ danh sách ảnh; đúng 1 ảnh `isPrimary`. */
  images: AdminProductImage[];
  /** Tối đa 2; rỗng = không có tuỳ chọn. Tuỳ chọn đã có giữ thứ tự, tuỳ chọn mới thêm ở sau. */
  options: ProductOptionRequest[];
  variants: ProductVariantRequest[];
}

/** Tồn kho khả dụng (stock − reserved). Sắp hết = 1..ngưỡng `app.inventory.low-stock-threshold` (mặc định 5). */
export type StockFilter = "IN_STOCK" | "LOW_STOCK" | "OUT_OF_STOCK";
export type AdminProductSort = "UPDATED_DESC" | "CREATED_DESC" | "NAME_ASC" | "PRICE_ASC" | "PRICE_DESC" | "STOCK_ASC";

export interface AdminProductQuery extends PageQuery {
  /** Tên, thương hiệu, slug — gõ không dấu được ("leu 2 nguoi"). */
  q?: string;
  /** Gồm cả danh mục con. */
  categoryId?: number;
  brand?: string;
  isActive?: boolean;
  stock?: StockFilter;
  sort?: AdminProductSort;
}

/* ───────────── Admin combo (FR-15, D-03) ───────────── */

export interface AdminComboSummary {
  id: number;
  name: string;
  slug: string;
  categoryId: number;
  categoryName: string;
  categoryPath: string[];
  comboPrice: Money;
  /** Tổng giá lẻ hiện tại của các sản phẩm thành phần (giá × số lượng). */
  itemsTotal: Money;
  itemCount: number;
  /** Tồn kho theo bộ, riêng với sản phẩm con (D-03). */
  stockQuantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  isActive: boolean;
  primaryImageUrl: string | null;
  updatedAt: string;
}

export interface AdminComboItem {
  productId: number;
  productName: string;
  productSlug: string;
  productImageUrl: string | null;
  productPrice: Money;
  productActive: boolean;
  variantId: number;
  /** null = biến thể mặc định (sản phẩm không có tuỳ chọn). */
  variantTitle: string | null;
  variantActive: boolean;
  /** Tồn kho khả dụng của biến thể — đóng gói trừ kho này (D-22). */
  productAvailableQuantity: number;
  /** Số cái trong một bộ. */
  quantity: number;
}

export interface AdminComboDetail extends Omit<AdminComboSummary, "categoryPath" | "itemCount"> {
  /** Số bộ còn đóng gói được ngay với tồn kho hiện tại của các sản phẩm thành phần. */
  maxPackable: number;
  description: string | null;
  items: AdminComboItem[];
  /** Ảnh tự tải lên (có thể rỗng); ảnh sản phẩm thành phần nằm ở `items[].productImageUrl`. */
  images: AdminProductImage[];
  createdAt: string;
}

export interface ComboRequest {
  name: string;
  slug?: string;
  description?: string | null;
  categoryId: number;
  comboPrice: Money;
  isActive: boolean;
  /** Ảnh tự tải lên, ≤ 10, có ảnh thì đúng 1 `isPrimary`. Combo còn hiển thị thêm ảnh của các sản phẩm thành phần (D-26). */
  images: AdminProductImage[];
  /** 2..20 sản phẩm khác nhau, mỗi dòng 1..99. `variantId` bắt buộc khi sản phẩm có tuỳ chọn. */
  items: { productId: number; variantId?: number | null; quantity: number }[];
}

export type AdminComboSort = "UPDATED_DESC" | "CREATED_DESC" | "NAME_ASC" | "PRICE_ASC" | "PRICE_DESC" | "STOCK_ASC";

export interface AdminComboQuery extends PageQuery {
  q?: string;
  categoryId?: number;
  isActive?: boolean;
  sort?: AdminComboSort;
}
