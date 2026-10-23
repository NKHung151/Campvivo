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

export interface ProductDetail extends ItemSummary {
  description: string | null;
  brand: string | null;
  category: CategoryRef;
  images: ImageRef[];
  campaign: { name: string; slug: string; endDate: string } | null;
  isFavorite?: boolean;
}

export interface Brand {
  name: string;
  productCount: number;
}

export type ProductSort = "newest" | "price_asc" | "price_desc";

export interface ProductQuery extends PageQuery {
  /** Slug danh mục — gồm cả danh mục con. */
  category?: string;
  brand?: string;
  minPrice?: Money;
  maxPrice?: Money;
  sortBy?: ProductSort;
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
  productCount: number;
  comboCount: number;
  children: AdminCategoryNode[];
}

export interface CategoryRequest {
  name: string;
  /** Bỏ trống → backend sinh từ tên. */
  slug?: string;
  parentId?: number | null;
}

export interface AdminProductSummary {
  id: number;
  name: string;
  slug: string;
  brand: string | null;
  categoryId: number;
  categoryName: string;
  price: Money;
  stockQuantity: number;
  reservedQuantity: number;
  isActive: boolean;
  primaryImageUrl: string | null;
  updatedAt: string;
}

export interface AdminProductImage {
  url: string;
  isPrimary: boolean;
  displayOrder: number;
}

export interface AdminProductDetail extends AdminProductSummary {
  description: string | null;
  images: AdminProductImage[];
  createdAt: string;
}

export interface ProductRequest {
  name: string;
  slug?: string;
  description?: string | null;
  brand?: string | null;
  price: Money;
  categoryId: number;
  isActive: boolean;
  /** Thay toàn bộ danh sách ảnh; đúng 1 ảnh `isPrimary`. */
  images: AdminProductImage[];
}

export interface AdminProductQuery extends PageQuery {
  q?: string;
  categoryId?: number;
  isActive?: boolean;
}
