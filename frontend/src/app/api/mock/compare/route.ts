import { getCards, getProduct } from "@/services/catalogService";
import { parseSpecs } from "@/lib/specs";

/** Mock endpoint: up to 3 products side by side for the compare widget. Replace with the catalog API. */
export async function GET(request: Request) {
  const slugs = (new URL(request.url).searchParams.get("slugs") ?? "").split(",").filter(Boolean).slice(0, 3);
  const cards = getCards();
  const rows = slugs.map((slug) => {
    const p = getProduct(slug);
    const c = cards[slug];
    return {
      slug,
      name: p?.name ?? c?.name ?? slug,
      img: c?.img ?? p?.images[0]?.thumb ?? "",
      price: p?.price ?? c?.price ?? null,
      oldPrice: p?.oldPrice ?? c?.oldPrice ?? null,
      brand: p?.brand ?? c?.brand ?? "",
      rating: p?.rating ?? c?.rating ?? null,
      specs: parseSpecs(p?.specsHtml),
    };
  });
  return Response.json(rows);
}
