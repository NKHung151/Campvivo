export interface InventoryBar {
  cls: string;
  bar?: string;
  label: string;
}

export interface Swatch {
  img: string;
  big?: string;
  name: string;
}

export interface ProductCard {
  slug: string;
  name: string;
  brand: string;
  img: string;
  price: number | null;
  oldPrice: number | null;
  memberPrice: number | null;
  discount: string | null;
  discountClass: string | null;
  installment: boolean;
  rating: number | null;
  sold: string | null;
  wepoint: string | null;
  inv?: InventoryBar | null;
  swatches?: Swatch[];
}

export interface LinkItem {
  name: string;
  href: string;
}

export interface MenuGroup extends LinkItem {
  img?: string;
  links: LinkItem[];
  more: LinkItem[];
}

export interface MenuItem extends LinkItem {
  special: boolean;
  groups: MenuGroup[];
  rightHtml: string | null;
}

export interface HomeData {
  promoBar: { href: string; html: string }[];
  hero: { href: string; img: string; alt: string; hotspots: { left: string; top: string; href: string }[] };
  slogan: { text: string; em: string };
  brandLogos: { name: string; href: string; img: string }[];
  flashSale: { title: string; href: string; items: ProductCard[] };
  groups: { title: string; href: string; desc: string; items: ProductCard[] }[];
  topCategories: { title: string; items: { name: string; href: string; img: string }[]; more: { text: string; btn: string; href: string } };
  banners: { col: string; href: string; title: string; img: string }[];
  campers: { title: string; desc: string; items: { img: string; alt: string; sku: string }[]; more: string };
  popular: { navs: { label: string; links: LinkItem[] }[]; banner: { href: string; img: string } };
  seoHtml: string;
  storesTitle: string;
  stores: { title: string; href: string; img: string }[];
}

export interface Filter {
  title: string;
  options: string[];
}

export interface Listing {
  slug: string;
  kind: "listing";
  name: string;
  intro: string | null;
  breadcrumbs: LinkItem[];
  sideCats: LinkItem[];
  subCats: (LinkItem & { img: string })[];
  filters: Filter[];
  guide: string | null;
  products: string[];
  bottomHtml: string | null;
  /** Full-width header image (e.g. New arrivals). */
  banner?: string | null;
}

export interface Landing {
  slug: string;
  kind: "landing";
  name: string;
  tiles: (LinkItem & { img: string })[];
  carousels: { title: string; href: string | null; items: string[] }[];
}

export type Category = Listing | Landing;

export interface VariantOption {
  name: string;
  img?: string;
  soldout: boolean;
  sku?: string;
}

export interface Review {
  name: string;
  date: string;
  rating: number;
  text: string;
}

export interface ProductDetail extends ProductCard {
  sku: string;
  breadcrumbs: LinkItem[];
  brandHref: string | null;
  images: { big: string; thumb: string; alt: string }[];
  short: string;
  warranty: string | null;
  discontinued: boolean;
  outOfStock: boolean;
  variantLabel: string | null;
  variants: VariantOption[];
  reviewCount: number;
  reviews: Review[];
  descriptionHtml: string;
  specsHtml: string | null;
  guideHtml: string | null;
  related: string[];
  saleEnds: string | null;
}

export interface Brand extends Omit<Listing, "kind"> {
  kind: "listing";
  header: { img: string | null; video: string | null; cls: string } | null;
  tabs: { products: string; articles: string | null } | null;
}

export interface BrandIndex {
  title: string;
  desc: string;
  items: (LinkItem & { img: string })[];
  tiles: (LinkItem & { img: string })[];
  carousels: { title: string; href: string | null; items: string[] }[];
}

export interface ArticleCard {
  slug: string;
  title: string;
  img: string;
  date: string;
  views: string;
  excerpt: string;
  listed?: boolean;
}

export interface Article extends ArticleCard {
  html: string;
  breadcrumbs: LinkItem[];
  related: { href: string; title: string; img: string }[];
}

export interface InfoPage {
  slug: string;
  title: string;
  updated: string;
  views: string;
  html: string;
  others: { name: string; extra: string; href: string }[];
  breadcrumbs: LinkItem[];
}

export interface InfoPages {
  nav: LinkItem[];
  pages: Record<string, InfoPage>;
}

export interface CartItem {
  key: string;
  slug: string;
  name: string;
  img: string;
  sku: string;
  variant: string | null;
  /** All variant names of the product, so the cart can switch "Phân loại". */
  options?: string[];
  variantLabel?: string | null;
  /** Stock for the chosen variant (drives "Sắp bán hết" note and qty max). */
  stock?: number | null;
  price: number;
  qty: number;
}

export interface Customer {
  gender: "Anh" | "Chị";
  name: string;
  phone: string;
  email: string;
  province: string;
  district: string;
  ward: string;
  street: string;
  note: string;
}

export interface Order {
  id: string;
  date: string;
  items: CartItem[];
  subtotal: number;
  voucher: number;
  member: number;
  points: number;
  shipping: string;
  total: number;
  payment: string;
  customer: Customer;
  status: string;
}

export interface User {
  phone: string;
  name: string;
  points: number;
}

/** Outdoor Journal (community articles): list index, categories and filters. */
export interface JournalIndex {
  title: string;
  breadcrumbs: LinkItem[];
  filters: { title: string; options: { name: string; href: string | null }[] }[];
  items: ArticleCard[];
  categories: Record<string, { name: string; items: ArticleCard[] }>;
}
