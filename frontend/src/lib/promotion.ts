import { vnd } from "@/lib/format";
import type { DiscountType, Money } from "@/types/api/common";

/** Mô tả voucher/chiến dịch dùng chung cho admin, popup voucher và PromoBar (FR-16, FR-17). */

interface DiscountLike {
  discountType: DiscountType;
  discountValue: Money;
  maxDiscount?: Money | null;
}

/** "Giảm 10%, tối đa 50.000đ" / "Giảm 50.000đ". */
export function discountLabel(d: DiscountLike): string {
  if (d.discountType === "PERCENTAGE") {
    return `Giảm ${d.discountValue}%` + (d.maxDiscount ? `, tối đa ${vnd(d.maxDiscount, "đ")}` : "");
  }
  return `Giảm ${vnd(d.discountValue, "đ")}`;
}

/** "Đơn từ 500.000đ" / "Mọi đơn hàng". */
export function minOrderLabel(minOrderValue: Money): string {
  return minOrderValue > 0 ? `Đơn từ ${vnd(minOrderValue, "đ")}` : "Mọi đơn hàng";
}

/** "HSD 30/11/2026". */
export function expiryLabel(iso: string): string {
  return "HSD " + new Date(iso).toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" });
}

/**
 * Slide PromoBar cho một mã công khai: "Nhập <b>CAMPVIVO10</b> – Giảm 10%, tối đa 50.000đ · Đơn từ 500.000đ".
 * An toàn để chèn HTML: mã chỉ gồm A-Z 0-9 _ - (backend kiểm), phần còn lại do hàm này tự sinh.
 */
export function voucherPromoSlide(v: DiscountLike & { code: string; minOrderValue: Money }): { href: string; html: string } {
  return { href: "/cart", html: `Nhập <b>${v.code}</b> – ${discountLabel(v)} · ${minOrderLabel(v.minOrderValue)}` };
}

const escapeHtml = (s: string) =>
  s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");

/** Slide PromoBar cho chiến dịch đang chạy: "<b>Xả hàng cuối mùa</b> – Giảm 20% · đến 30/11". Tên do admin nhập → escape. */
export function campaignPromoSlide(c: DiscountLike & { name: string; slug: string; endDate: string }): { href: string; html: string } {
  // Ghép tay "dd/MM": dấu phân cách của toLocaleDateString("vi-VN") khác nhau giữa Node và trình duyệt
  const parts = new Intl.DateTimeFormat("vi-VN", { timeZone: "Asia/Ho_Chi_Minh", day: "2-digit", month: "2-digit" }).formatToParts(new Date(c.endDate));
  const until = `${parts.find((p) => p.type === "day")?.value}/${parts.find((p) => p.type === "month")?.value}`;
  return { href: `/promotions/${c.slug}`, html: `<b>${escapeHtml(c.name)}</b> – ${discountLabel(c)} · đến ${until}` };
}

/** Xen kẽ 2 danh sách: a1, b1, a2, b2… (phần dư nối cuối). */
export function interleave<T>(a: T[], b: T[]): T[] {
  const out: T[] = [];
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    if (i < a.length) out.push(a[i]);
    if (i < b.length) out.push(b[i]);
  }
  return out;
}

/**
 * Giá xem trước khi chỉ áp một chiến dịch (BR-6) — khớp promotion.Discounts / Product.salePrice ở backend (HALF_UP,
 * Math.round đúng với số dương). Chỉ để hiển thị ở admin; giá bán thật luôn lấy từ API.
 */
export function campaignPrice(price: Money, d: DiscountLike): Money {
  if (d.discountType === "PERCENTAGE") return Math.round((price * (100 - d.discountValue)) / 100);
  return Math.max(price - d.discountValue, 0);
}

/** % giảm hiển thị trên badge giá (làm tròn xuống, 0 khi không giảm). */
export function percentOff(originalPrice: Money, price: Money): number {
  return originalPrice > 0 && price < originalPrice ? Math.floor(((originalPrice - price) / originalPrice) * 100) : 0;
}
