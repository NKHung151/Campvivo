import { cache } from "react";
import type {
  Brand,
  CategoryNode,
  ComboDetail,
  ComboQuery,
  ComboSummary,
  ItemSummary,
  ProductDetail,
  ProductQuery,
} from "@/types/api/catalog";
import type { Page } from "@/types/api/common";
import { isApiError } from "@/services/apiError";
import { serverGet, serverGetPage, type ServerRequestOptions } from "@/services/serverApi";

/**
 * Catalog API public (docs/02-ky-thuat/api.md mục 4.3), gọi từ server component / route handler qua `serverApi`.
 * Không cache: admin sửa sản phẩm là storefront thấy ngay.
 */

/** Bỏ tham số rỗng (ô lọc để trống) khỏi query. */
const params = (p: Record<string, string | number | undefined>): ServerRequestOptions["params"] =>
  Object.fromEntries(Object.entries(p).filter(([, v]) => v !== undefined && v !== ""));

/** 404 (slug không tồn tại hoặc đã ẩn) → null. */
async function orNull<T>(request: Promise<T>): Promise<T | null> {
  try {
    return await request;
  } catch (e) {
    if (isApiError(e) && e.status === 404) return null;
    throw e;
  }
}

export async function listProducts(query: ProductQuery): Promise<Page<ItemSummary>> {
  const { slugs, ...rest } = query;
  return serverGetPage<ItemSummary>("/products", { params: params({ ...rest, slugs: slugs?.join(",") }) });
}

/** Toàn bộ sản phẩm khớp bộ lọc (trang danh mục/thương hiệu lọc và phân trang phía client). */
export async function listAllProducts(
  query: Omit<ProductQuery, "page" | "limit">,
  max = 1000
): Promise<ItemSummary[]> {
  const out: ItemSummary[] = [];
  for (let page = 1; out.length < max; page++) {
    const res = await listProducts({ ...query, page, limit: 100 });
    out.push(...res.data);
    if (page >= res.pagination.totalPages) break;
  }
  return out;
}

/** Sản phẩm theo danh sách slug, giữ đúng thứ tự; slug không có (hoặc đang ẩn) bị bỏ qua. */
export async function productsBySlugs(slugs: string[]): Promise<ItemSummary[]> {
  const unique = Array.from(new Set(slugs.filter((s) => /^[a-z0-9-]+$/.test(s))));
  const found = new Map<string, ItemSummary>();
  for (let i = 0; i < unique.length; i += 100) {
    const chunk = unique.slice(i, i + 100);
    const res = await listProducts({ slugs: chunk, limit: 100 });
    res.data.forEach((p) => found.set(p.slug, p));
  }
  return slugs.map((s) => found.get(s)).filter((p): p is ItemSummary => Boolean(p));
}

export const getProductDetail = cache(async (slug: string): Promise<ProductDetail | null> => {
  if (!/^[a-z0-9-]+$/.test(slug)) return null;
  return orNull(serverGet<ProductDetail>(`/products/${slug}`));
});

/** Toàn bộ combo đang bán khớp bộ lọc (số combo ít — trang combo sắp xếp/lọc phía client). */
export async function listAllCombos(query: Omit<ComboQuery, "page" | "limit"> = {}): Promise<ComboSummary[]> {
  const out: ComboSummary[] = [];
  for (let page = 1; page <= 10; page++) {
    const body: Page<ComboSummary> = await serverGetPage<ComboSummary>("/combos", { params: params({ ...query, page, limit: 100 }) });
    out.push(...body.data);
    if (page >= body.pagination.totalPages) break;
  }
  return out;
}

export const getComboDetail = cache(async (slug: string): Promise<ComboDetail | null> => {
  if (!/^[a-z0-9-]+$/.test(slug)) return null;
  return orNull(serverGet<ComboDetail>(`/combos/${slug}`));
});

export const getCategoryTree = cache(async (): Promise<CategoryNode[]> => {
  return serverGet<CategoryNode[]>("/categories");
});

export const getBrandList = cache(async (): Promise<Brand[]> => {
  return serverGet<Brand[]>("/brands");
});
