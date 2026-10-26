import { getProductDetail } from "@/services/catalogApi";

/** Tối đa 3 sản phẩm cho khung so sánh — giá, thương hiệu, thông số từ catalog API. */
export async function GET(request: Request) {
  const slugs = (new URL(request.url).searchParams.get("slugs") ?? "")
    .split(",")
    .filter(Boolean)
    .slice(0, 3);
  const details = await Promise.all(slugs.map((slug) => getProductDetail(slug)));
  const rows = details
    .filter((p) => p !== null)
    .map((p) => ({
      slug: p.slug,
      name: p.name,
      img: p.primaryImageUrl ?? "",
      price: p.price,
      oldPrice: p.originalPrice > p.price ? p.originalPrice : null,
      brand: p.brand ?? "",
      rating: p.averageRating === null ? null : p.averageRating * 20,
      specs: p.specs.map((s) => [s.name, s.value] as [string, string]),
    }));
  return Response.json(rows);
}
