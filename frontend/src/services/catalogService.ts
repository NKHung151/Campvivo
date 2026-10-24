import fs from "node:fs";
import path from "node:path";
import { cache } from "react";
import type {
  Article,
  ArticleCard,
  Brand,
  BrandIndex,
  Category,
  HomeData,
  InfoPages,
  JournalIndex,
  Landing,
  LinkItem,
  Listing,
  MenuItem,
  ProductCard,
} from "@/types/shop";
import type { ProductFull, PdpKb, PdpStatic, RelatedItem, SidebarItem } from "@/types/product";
import type { CategoryNode, ComboSummary, ItemSummary, ProductDetail } from "@/types/api/catalog";
import { cleanMock } from "@/services/mockContent";
import {
  getBrandList,
  getCategoryTree,
  getProductDetail,
  listAllCombos,
  listAllProducts,
  listProducts,
  productsBySlugs,
} from "@/services/catalogApi";
import { brandHref, categoryHref, comboHref, vnd } from "@/lib/format";
import { toSlug } from "@/lib/slug";

/*
 * Dữ liệu storefront.
 * - Sản phẩm, danh mục, thương hiệu: lấy từ backend (catalogApi) — admin sửa là thấy ngay.
 * - Nội dung chưa có trong DB (banner trang chủ, giới thiệu danh mục, ảnh menu, bài viết, trang thông tin…)
 *   vẫn đọc từ src/mocks/data; danh sách sản phẩm trong các khối đó chỉ dùng slug, giá/ảnh/tồn kho lấy từ DB.
 */
const DATA_DIR = path.join(process.cwd(), "src", "mocks", "data");

function readRaw(rel: string): unknown {
  const file = path.join(DATA_DIR, rel);
  if (!fs.existsSync(file)) return null;
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function readJson<T>(rel: string): T | null {
  const raw = readRaw(rel);
  return raw === null ? null : cleanMock(raw as T);
}

/* ───────────── Nội dung tĩnh (mock) ───────────── */

export const getHome = cache(() => readJson<HomeData>("home.json")!);
const getMockMenu = cache(() => readJson<MenuItem[]>("menu.json") ?? []);
const getMockCategories = cache(() => readJson<Record<string, Category>>("categories.json") ?? {});
const getMockBrands = cache(() => readJson<Record<string, Brand>>("brands.json") ?? {});
const getMockBrandIndex = cache(() => readJson<BrandIndex>("brand-index.json")!);
export const getArticleIndex = cache(() => readJson<ArticleCard[]>("articles.json") ?? []);
export const getArticleFilters = cache(
  () =>
    readJson<{ title: string; options: { name: string; href: string | null }[] }[]>(
      "article-filters.json"
    ) ?? []
);
export const getInfoPages = cache(() => readJson<InfoPages>("info-pages.json")!);
/** Knowledge hub sections; the page renders its own heading/intro, so the mock header block is dropped. */
export const getCampvivology = cache(() => {
  const raw = readRaw("campvivology.json") as { html: string; css: string } | null;
  if (!raw) return null;
  const html = raw.html.replace(
    /<h1 class="campvivology_heading">[\s\S]*?<\/h1>\s*<div class="campvivology_desc">[\s\S]*?<\/div>/,
    ""
  );
  return cleanMock({ ...raw, html });
});
export const getSkuMap = cache(() => readJson<Record<string, string>>("sku-map.json") ?? {});
const getMockPromos = cache(
  () =>
    readJson<Record<string, { title: string; desc: string; items: ProductCard[] }>>(
      "promos.json"
    ) ?? {}
);
export const getCartData = cache(() =>
  readJson<{ csvcHtml: string; pvcHtml: string; provinces: { v: string; n: string }[] }>(
    "cart.json"
  )!
);
export const getPdpKb = cache(() => readJson<PdpKb>("pdp-kb.json")!);
export const getPdpStatic = cache(() => readJson<PdpStatic>("pdp-static.json")!);

export const getJournal = cache(() => readJson<JournalIndex>("journal.json")!);

export const getJournalArticle = cache((slug: string) => {
  if (!/^[a-z0-9-]+$/.test(slug)) return null;
  return readJson<Article>(`journal/${slug}.json`);
});

export const getArticle = cache((slug: string) => {
  if (!/^[a-z0-9-]+$/.test(slug)) return null;
  return readJson<Article>(`articles/${slug}.json`);
});

/** Articles whose breadcrumb category matches `/blog/category/<cat>` (the list cards don't carry categories). */
export const articlesInCategory = cache((catHref: string): ArticleCard[] =>
  getArticleIndex().filter((a) => getArticle(a.slug)?.breadcrumbs.some((b) => b.href === catHref))
);

/* ───────────── Sản phẩm (API) ───────────── */

const LOW_STOCK = 5;

/** ItemSummary (API) → thẻ sản phẩm của storefront. */
export function toCard(p: ItemSummary): ProductCard {
  const sale = p.originalPrice > p.price;
  return {
    slug: p.slug,
    name: p.name,
    brand: p.brand ?? "",
    img: p.primaryImageUrl ?? "",
    price: p.price,
    oldPrice: sale ? p.originalPrice : null,
    discount: sale ? `${Math.round((1 - p.price / p.originalPrice) * 100)}%` : null,
    discountClass: null,
    installment: false,
    rating: p.averageRating === null ? null : p.averageRating * 20,
    sold: null,
    inv:
      p.availableQuantity <= 0
        ? { cls: "inv-zero", bar: "0%", label: "Hết hàng" }
        : p.availableQuantity <= LOW_STOCK
          ? { cls: "inv-critical", bar: "15%", label: `Chỉ còn ${p.availableQuantity}` }
          : null,
  };
}

/** Thẻ sản phẩm theo danh sách slug (khối chọn sẵn, đã xem, yêu thích). Slug không còn bán bị bỏ. */
export async function cardsFor(slugs: string[]): Promise<ProductCard[]> {
  return (await productsBySlugs(slugs)).map(toCard);
}

export async function searchProducts(query: string): Promise<ProductCard[]> {
  if (!query.trim()) return [];
  return (await listProducts({ q: query.trim(), limit: 100 })).data.map(toCard);
}

/** Bảng thông số (`products.specs`) dưới dạng HTML cho tab "Đặc tính". */
function specsTable(specs: { name: string; value: string }[]): string | null {
  if (!specs.length) return null;
  const esc = (s: string) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  const rows = specs
    .map((s) => `<tr><td>${esc(s.name)}</td><td>${esc(s.value)}</td></tr>`)
    .join("");
  return `<table class="cv-specs"><tbody>${rows}</tbody></table>`;
}

function toRelated(p: ItemSummary): RelatedItem {
  const c = toCard(p);
  return {
    slug: c.slug,
    name: c.name,
    brand: c.brand,
    img: c.img,
    price: c.price,
    oldPrice: c.oldPrice,
    discount: c.discount,
    discountClass: null,
    rating: c.rating ?? 100,
  };
}

function toSidebar(p: ItemSummary): SidebarItem {
  return {
    slug: p.slug,
    href: `/products/${p.slug}`,
    name: p.name,
    img: p.primaryImageUrl ?? "",
    brand: p.brand ?? "",
    price: vnd(p.price),
    rating: 100,
  };
}

function toProductFull(
  d: ProductDetail,
  related: ItemSummary[],
  newest: ItemSummary[]
): ProductFull {
  const images = [...d.images].sort((a, b) => Number(b.isPrimary) - Number(a.isPrimary));
  const sale = d.originalPrice > d.price;
  return {
    slug: d.slug,
    name: d.name,
    brand: d.brand ?? "",
    brandHref: d.brand ? brandHref(toSlug(d.brand)) : null,
    sku: "",
    rating: d.averageRating === null ? 0 : d.averageRating * 20,
    reviewCount: d.reviewCount,
    sold: null,
    short: d.shortDescription ?? "",
    stockUrgency:
      d.availableQuantity > 0 && d.availableQuantity <= LOW_STOCK
        ? `Chỉ còn ${d.availableQuantity} sản phẩm`
        : null,
    price: d.price,
    oldPrice: sale ? d.originalPrice : null,
    savePct: sale ? `${Math.round((1 - d.price / d.originalPrice) * 100)}%` : null,
    saleEnds: d.campaign?.endDate ?? null,
    // DB không lưu bảo hành → không hiện dòng "Bảo hành" trên trang sản phẩm
    warranty: null,
    maxQty: d.availableQuantity,
    // Không có biến thể (D-24): mỗi màu/size là một sản phẩm riêng → không hiện ô chọn phân loại
    variantLabel: null,
    variants: [],
    viewsToday: null,
    images: images.map((i) => ({ big: i.url, thumb: i.url, alt: d.name })),
    breadcrumbs: [
      { name: "Sản phẩm", href: "/products" },
      ...d.category.path.map((c) => ({ name: c.name, href: categoryHref(c.slug) })),
    ],
    categoryName: d.category.name,
    catImg: images[0]?.url ?? null,
    descriptionHtml: d.description,
    specsHtml: specsTable(d.specs),
    guideHtml: null,
    reviews: [],
    social: null,
    fbt: [],
    articles: [],
    sidebar: {
      title: "Sản phẩm mới",
      items: newest
        .filter((p) => p.slug !== d.slug)
        .slice(0, 6)
        .map(toSidebar),
    },
    related: {
      title: "Sản phẩm cùng loại",
      items: related
        .filter((p) => p.slug !== d.slug)
        .slice(0, 12)
        .map(toRelated),
    },
  };
}

export const getProduct = cache(async (slug: string): Promise<ProductFull | null> => {
  const d = await getProductDetail(slug);
  if (!d) return null;
  const root = d.category.path[0]?.slug ?? d.category.slug;
  const [related, newest] = await Promise.all([
    listProducts({ category: d.category.slug, limit: 13 }),
    listProducts({ category: root, sortBy: "newest", limit: 7 }),
  ]);
  return toProductFull(d, related.data, newest.data);
});

/* ───────────── Danh mục & menu ───────────── */

function findWithAncestors(
  nodes: CategoryNode[],
  slug: string,
  trail: CategoryNode[] = []
): { node: CategoryNode; ancestors: CategoryNode[] } | null {
  for (const n of nodes) {
    if (n.slug === slug) return { node: n, ancestors: trail };
    const hit = findWithAncestors(n.children, slug, [...trail, n]);
    if (hit) return hit;
  }
  return null;
}

const catLink = (c: CategoryNode): LinkItem => ({ name: c.name, href: categoryHref(c.slug) });

/**
 * Menu chính: danh mục gốc → nhóm (cấp 2) → link (cấp 3) lấy từ cây danh mục trong DB, đúng thứ tự API trả về.
 * Banner bên phải lấy từ menu mẫu nếu trùng đường dẫn; thêm mục Combo (nhóm theo danh mục cấp 1) và
 * mục Thương hiệu/khuyến mãi của menu mẫu.
 */
export const getMenu = cache(async (): Promise<MenuItem[]> => {
  const mock = getMockMenu();
  const mockByHref = new Map(mock.map((m) => [m.href, m]));
  const tree = await getCategoryTree().catch(() => [] as CategoryNode[]);
  const fromDb: MenuItem[] = tree.map((root) => ({
    ...catLink(root),
    special: false,
    rightHtml: mockByHref.get(categoryHref(root.slug))?.rightHtml ?? null,
    groups: root.children.map((g) => ({
      ...catLink(g),
      links: g.children.slice(0, 3).map(catLink),
      more: g.children.slice(3).map(catLink),
    })),
  }));
  const combos = await listAllCombos().catch(() => [] as ComboSummary[]);
  const comboMenu: MenuItem[] = combos.length
    ? [
        {
          name: "Combo",
          href: "/combos",
          special: false,
          rightHtml: null,
          groups: comboGroups(combos).map(({ category, items }) => {
            const links = items.map((c) => ({ name: c.name, href: comboHref(c.slug) }));
            return { ...comboCategoryLink(category), links: links.slice(0, 3), more: links.slice(3) };
          }),
        },
      ]
    : [];
  return [...fromDb, ...comboMenu, ...mock.filter((m) => !m.href.startsWith("/categories/"))];
});

/* ───────────── Combo (chỉ xem — FR-07) ───────────── */

type ComboCategory = ComboSummary["category"];

const comboCategoryLink = (c: ComboCategory): LinkItem => ({ name: c.name, href: `/combos?category=${c.slug}` });

/** Combo nhóm theo danh mục cấp 1, giữ thứ tự xuất hiện. */
function comboGroups(combos: ComboSummary[]): { category: ComboCategory; items: ComboSummary[] }[] {
  const groups = new Map<number, { category: ComboCategory; items: ComboSummary[] }>();
  for (const c of combos) {
    const g = groups.get(c.category.id) ?? { category: c.category, items: [] };
    g.items.push(c);
    groups.set(c.category.id, g);
  }
  return Array.from(groups.values());
}

/** Thẻ combo: giá gạch = tổng giá lẻ các món (khi combo rẻ hơn), dòng thương hiệu = số món. */
export function comboCard(c: ComboSummary): ProductCard {
  const saving = c.itemsTotal > c.price;
  return {
    ...toCard(c),
    brand: `Combo ${c.itemCount} món`,
    oldPrice: saving ? c.itemsTotal : null,
    discount: saving ? `${Math.round((1 - c.price / c.itemsTotal) * 100)}%` : null,
    href: comboHref(c.slug),
  };
}

/** Trang /combos: toàn bộ combo đang bán, lọc theo danh mục cấp 1 (`?category=`). Danh mục không có combo → null. */
export const getComboListing = cache(
  async (
    categorySlug?: string
  ): Promise<{ title: string; breadcrumbs: LinkItem[]; sideCats: LinkItem[]; products: ProductCard[] } | null> => {
    const all = await listAllCombos();
    const groups = comboGroups(all);
    const current = categorySlug ? groups.find((g) => g.category.slug === categorySlug) : undefined;
    if (categorySlug && !current) return null;
    const breadcrumbs: LinkItem[] = [{ name: "Combo", href: "/combos" }];
    if (current) breadcrumbs.push(comboCategoryLink(current.category));
    return {
      title: current ? `Combo ${current.category.name}` : "Combo",
      breadcrumbs,
      sideCats: groups.length > 1 ? [{ name: "Tất cả combo", href: "/combos" }, ...groups.map((g) => comboCategoryLink(g.category))] : [],
      products: (current?.items ?? all).map(comboCard),
    };
  }
);

/**
 * Trang danh mục. Danh mục có trong DB: tên, đường dẫn, danh mục con và sản phẩm từ DB; phần giới thiệu/banner
 * lấy từ dữ liệu mẫu nếu trùng slug. Danh mục chỉ có trong dữ liệu mẫu (link cũ): giữ bố cục mẫu, sản phẩm lấy từ DB.
 */
export const getCategoryPage = cache(
  async (
    slug: string
  ): Promise<{
    category: Category;
    products: ProductCard[];
    carousels: { title: string; href: string | null; items: ProductCard[] }[];
  } | null> => {
    const mock = getMockCategories()[slug];
    const hit = findWithAncestors(await getCategoryTree(), slug);
    if (hit) {
      const { node, ancestors } = hit;
      const items = await listAllProducts({ category: slug });
      const mockListing = mock?.kind === "listing" ? mock : null;
      const subImg = new Map((mockListing?.subCats ?? []).map((s) => [s.href, s.img]));
      const children = node.children.map(catLink);
      const parent = ancestors[ancestors.length - 1];
      const brands = Array.from(
        new Set(items.map((p) => p.brand).filter((b): b is string => Boolean(b)))
      ).sort((a, b) => a.localeCompare(b, "vi"));
      const category: Listing = {
        slug,
        kind: "listing",
        name: node.name,
        intro: mockListing?.intro ?? null,
        breadcrumbs: [{ name: "Sản phẩm", href: "/products" }, ...ancestors.map(catLink)],
        sideCats: children.length
          ? children
          : (parent?.children ?? []).filter((c) => c.slug !== slug).map(catLink),
        subCats: children
          .filter((c) => subImg.get(c.href))
          .map((c) => ({ ...c, img: subImg.get(c.href)! })),
        filters: brands.length > 1 ? [{ title: "Thương hiệu", options: brands }] : [],
        guide: mockListing?.guide ?? null,
        products: [],
        bottomHtml: mockListing?.bottomHtml ?? null,
        banner: mockListing?.banner ?? null,
      };
      return { category, products: items.map(toCard), carousels: [] };
    }
    if (!mock) return null;
    if (mock.kind === "landing")
      return { category: mock, products: [], carousels: await resolveCarousels(mock) };
    return { category: mock, products: await cardsFor(mock.products), carousels: [] };
  }
);

async function resolveCarousels(l: Pick<Landing, "carousels">) {
  const out = await Promise.all(
    l.carousels.map(async (k) => ({ ...k, items: await cardsFor(k.items) }))
  );
  return out.filter((k) => k.items.length > 0);
}

/** Trang /products: bố cục mẫu (ô danh mục + các dải sản phẩm), sản phẩm lấy từ DB. */
export async function getAllProductsLanding() {
  const c = getMockCategories()["__all__"];
  if (!c || c.kind !== "landing") return null;
  return { tiles: c.tiles, carousels: await resolveCarousels(c) };
}

/* ───────────── Thương hiệu ───────────── */

const brandKey = (name: string) => toSlug(name);

/** Trang thương hiệu: tìm theo slug của tên thương hiệu trong DB (hoặc slug cũ của dữ liệu mẫu). */
export const getBrandPage = cache(
  async (slug: string): Promise<{ brand: Brand; products: ProductCard[] } | null> => {
    const mock = getMockBrands()[slug];
    const list = await getBrandList();
    const db =
      list.find((b) => brandKey(b.name) === slug) ??
      (mock ? list.find((b) => brandKey(b.name) === brandKey(mock.name)) : undefined);
    if (!db) return mock ? { brand: mock, products: await cardsFor(mock.products) } : null;
    const items = await listAllProducts({ brand: db.name });
    const brand: Brand = mock
      ? { ...mock, tabs: mock.tabs && { ...mock.tabs, products: `Sản phẩm (${items.length})` } }
      : {
          slug,
          kind: "listing",
          name: db.name,
          intro: null,
          breadcrumbs: [{ name: "Thương hiệu", href: "/brands" }],
          sideCats: [],
          subCats: [],
          filters: [],
          guide: null,
          products: [],
          bottomHtml: null,
          header: null,
          tabs: null,
        };
    return { brand, products: items.map(toCard) };
  }
);

/** Danh sách thương hiệu từ DB (logo lấy từ dữ liệu mẫu nếu có) + các dải sản phẩm mẫu. */
export async function getBrandIndex() {
  const idx = getMockBrandIndex();
  const logos = new Map(idx.items.map((b) => [brandKey(b.name), b.img]));
  idx.items.forEach((b) => logos.set(b.href.replace(/^\/brands\//, ""), b.img));
  const brands = await getBrandList();
  return {
    title: idx.title,
    desc: idx.desc,
    items: brands.map((b) => ({
      name: b.name,
      href: brandHref(brandKey(b.name)),
      img: logos.get(brandKey(b.name)) ?? "",
    })),
    carousels: await resolveCarousels(idx),
  };
}

/* ───────────── Trang chủ & khuyến mãi ───────────── */

/** Khối sản phẩm trang chủ: giữ danh sách chọn sẵn của dữ liệu mẫu, giá/ảnh/tồn kho lấy từ DB. */
export async function getHomeProducts() {
  const home = getHome();
  const [flash, ...groups] = await Promise.all([
    cardsFor(home.flashSale.items.map((c) => c.slug)),
    ...home.groups.map((g) => cardsFor(g.items.map((c) => c.slug))),
  ]);
  return {
    flashSale: { ...home.flashSale, items: flash },
    groups: home.groups
      .map((g, i) => ({ ...g, items: groups[i] }))
      .filter((g) => g.items.length > 0),
  };
}

export async function getPromo(slug: string) {
  const p = getMockPromos()[slug];
  if (!p) return null;
  return { ...p, items: await cardsFor(p.items.map((c) => c.slug)) };
}

/* ───────────── Tìm kiếm bài viết ───────────── */

const fold = (s: string) => s.toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/đ/g, "d");

/** Article tab of the search page: title + excerpt, accent-insensitive, newest first as listed. */
export function searchArticles(query: string): ArticleCard[] {
  const terms = fold(query).split(/\s+/).filter(Boolean);
  if (!terms.length) return [];
  return getArticleIndex().filter((a) => {
    const hay = fold(`${a.title} ${a.excerpt}`);
    return terms.every((t) => hay.includes(t));
  });
}

/** Route slug as typed in the URL (may be percent-encoded). */
export function routeSlug(raw: string): string {
  return decodeURIComponent(raw);
}
