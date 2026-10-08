import type { LinkItem, Review } from "@/types/shop";

export interface SidebarItem {
  slug: string | null;
  href: string;
  name: string;
  img: string;
  brand: string;
  price: string;
  rating: number;
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
  rating: number;
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
  memberPrice: number | null;
  warranty: string | null;
  discontinued: boolean;
  preOrder: boolean;
  maxQty: number | null;
  variantLabel: string | null;
  variants: { name: string; inv: number }[];
  wepoint: string | null;
  viewsToday: string | null;
  images: { big: string; thumb: string; alt: string }[];
  breadcrumbs: LinkItem[];
  categoryName: string | null;
  catImg: string | null;
  descriptionHtml: string | null;
  specsHtml: string | null;
  guideHtml: string | null;
  reviews: Review[];
  social: { sold: number; rating: number; satisf: number } | null;
  fbt: FbtItem[];
  articles: { title: string; url: string }[];
  sidebar: { title: string; items: SidebarItem[] };
  related: { title: string; items: RelatedItem[] };
}

export interface PdpKb {
  journeyRules: { keys: string[]; journeys: { icon: string; title: string; sub: string }[] }[];
  educationTips: { keys: string[]; tips: string[] }[];
  genericJourneys: { icon: string; title: string; sub: string }[];
}

export interface PdpStatic {
  voucherHtml: string;
  voucherImg: string;
  benefitsHtml: string;
  memberDesHtml: string;
  policyHtml: string;
  pointDesHtml: string;
}
