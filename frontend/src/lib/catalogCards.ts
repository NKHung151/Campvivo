import type { ProductCard } from "@/types/shop";
import type { ComboSummary, ItemSummary } from "@/types/api/catalog";
import { comboHref } from "@/lib/format";

/** Đổi `ItemSummary` / `ComboSummary` (API) thành thẻ storefront — không phụ thuộc server, dùng được ở client component. */

/** Khả dụng ≤ ngưỡng này hiện "Chỉ còn N" (cùng mặc định `app.inventory.low-stock-threshold`). */
export const LOW_STOCK = 5;

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
    inStock: p.availableQuantity > 0,
    inv:
      p.availableQuantity <= 0
        ? { cls: "inv-zero", bar: "0%", label: "Hết hàng" }
        : p.availableQuantity <= LOW_STOCK
          ? { cls: "inv-critical", bar: "15%", label: `Chỉ còn ${p.availableQuantity}` }
          : null,
  };
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
