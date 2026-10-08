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
  MenuItem,
  ProductCard,
} from "@/types/shop";
import type { ProductFull, PdpKb, PdpStatic } from "@/types/product";
import { cleanMock } from "@/services/mockContent";

// Mock catalogue (src/mocks/data) read on the server. Swap these loaders for apiClient calls when the backend is ready.
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

export const getHome = cache(() => readJson<HomeData>("home.json")!);
export const getMenu = cache(() => readJson<MenuItem[]>("menu.json") ?? []);
export const getCards = cache(() => readJson<Record<string, ProductCard>>("cards.json") ?? {});
export const getCategories = cache(() => readJson<Record<string, Category>>("categories.json") ?? {});
export const getBrands = cache(() => readJson<Record<string, Brand>>("brands.json") ?? {});
export const getBrandIndex = cache(() => readJson<BrandIndex>("brand-index.json")!);
export const getArticleIndex = cache(() => readJson<ArticleCard[]>("articles.json") ?? []);
export const getArticleFilters = cache(
  () => readJson<{ title: string; options: { name: string; href: string | null }[] }[]>("article-filters.json") ?? [],
);
export const getInfoPages = cache(() => readJson<InfoPages>("info-pages.json")!);
/** Knowledge hub sections; the page renders its own heading/intro, so the mock header block is dropped. */
export const getCampvivology = cache(() => {
  const raw = readRaw("campvivology.json") as { html: string; css: string } | null;
  if (!raw) return null;
  const html = raw.html.replace(/<h1 class="campvivology_heading">[\s\S]*?<\/h1>\s*<div class="campvivology_desc">[\s\S]*?<\/div>/, "");
  return cleanMock({ ...raw, html });
});
export const getSkuMap = cache(() => readJson<Record<string, string>>("sku-map.json") ?? {});
export const getPromos = cache(
  () => readJson<Record<string, { title: string; desc: string; items: ProductCard[] }>>("promos.json") ?? {},
);
export const getCartData = cache(
  () => readJson<{ csvcHtml: string; pvcHtml: string; provinces: { v: string; n: string }[] }>("cart.json")!,
);
export const getPdpKb =cache(() => readJson<PdpKb>("pdp-kb.json")!);
export const getPdpStatic = cache(() => readJson<PdpStatic>("pdp-static.json")!);

export const getProduct = cache((slug: string) => {
  if (!/^[a-z0-9-]+$/.test(slug)) return null;
  return readJson<ProductFull>(`products/${slug}.json`);
});

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
  getArticleIndex().filter((a) => getArticle(a.slug)?.breadcrumbs.some((b) => b.href === catHref)),
);

export function cardsFor(slugs: string[]): ProductCard[] {
  const all = getCards();
  return slugs.map((s) => all[s]).filter((c): c is ProductCard => Boolean(c));
}

const fold = (s: string) =>
  s
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/g, "d");

/** Search over the catalogue (name + brand), accent-insensitive. */
export function searchProducts(query: string): ProductCard[] {
  const terms = fold(query).split(/\s+/).filter(Boolean);
  if (!terms.length) return [];
  return Object.values(getCards()).filter((c) => {
    const hay = fold(`${c.name} ${c.brand}`);
    return terms.every((t) => hay.includes(t));
  });
}

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
