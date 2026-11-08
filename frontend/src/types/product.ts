import type { LinkItem } from "@/types/shop";

export interface SidebarItem {
  slug: string | null;
  href: string;
  name: string;
  img: string;
  brand: string;
  price: string;
  /** 0–100 (% chiều rộng sao); null = chưa có đánh giá. */
  rating: number | null;
}

export interface RelatedItem {
  slug: string | null;
  name: string;
  brand: string;
  img: string;
  price: number | null;
  oldPrice: number | null;
  discount: string | null;
  discountClass: string | null;
  /** 0–100 (% chiều rộng sao); null = chưa có đánh giá. */
  rating: number | null;
}

export interface FbtItem {
  name: string;
  price: number;
  disc: string;
  img: string;
  link: string;
  rating: number;
  sold: number;
}

export interface ProductFull {
  /** id sản phẩm (API) — dùng cho giỏ hàng. */
  id: number;
  slug: string;
  name: string;
  brand: string;
  brandHref: string | null;
  sku: string;
  rating: number;
  reviewCount: number;
  sold: string | null;
  short: string;
  stockUrgency: string | null;
  price: number | null;
  oldPrice: number | null;
  savePct: string | null;
  saleEnds: string | null;
  /** Chiến dịch đang áp cho giá (FR-17) — link tới /promotions/[slug]. */
  campaign?: { name: string; slug: string } | null;
  warranty: string | null;
  maxQty: number | null;
  /** Tuỳ chọn (tối đa 2, vd. Màu sắc → Size — D-37); rỗng = sản phẩm không có biến thể. */
  options: { name: string; values: string[] }[];
  /** Biến thể đang bán; `values` theo thứ tự `options`, `inv` = số còn bán được. Rỗng khi không có tuỳ chọn. */
  variants: { id: number; values: string[]; inv: number }[];
  viewsToday: string | null;
  images: { big: string; thumb: string; alt: string }[];
  breadcrumbs: LinkItem[];
  categoryName: string | null;
  catImg: string | null;
  descriptionHtml: string | null;
  specsHtml: string | null;
  social: { sold: number; rating: number; satisf: number } | null;
  fbt: FbtItem[];
  articles: { title: string; url: string }[];
  sidebar: { title: string; items: SidebarItem[] };
  related: { title: string; items: RelatedItem[] };
}

export interface PdpStatic {
  voucherHtml: string;
  voucherImg: string;
  benefitsHtml: string;
  policyHtml: string;
}
