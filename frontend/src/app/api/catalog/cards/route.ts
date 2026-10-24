import { cardsFor } from "@/services/catalogService";

/** Thẻ sản phẩm theo danh sách slug ("Sản phẩm đã xem", yêu thích) — dữ liệu từ catalog API. */
export async function GET(request: Request) {
  const slugs = (new URL(request.url).searchParams.get("slugs") ?? "")
    .split(",")
    .filter(Boolean)
    .slice(0, 40);
  return Response.json(await cardsFor(slugs));
}
