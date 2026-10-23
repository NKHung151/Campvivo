import { cardsFor } from "@/services/catalogService";

/** Mock endpoint: card data for a list of slugs ("Sản phẩm đã xem", wishlist). Replace with the catalog API. */
export async function GET(request: Request) {
  const slugs = (new URL(request.url).searchParams.get("slugs") ?? "").split(",").filter(Boolean).slice(0, 40);
  return Response.json(cardsFor(slugs));
}
