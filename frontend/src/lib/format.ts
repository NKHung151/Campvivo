/** Format a VND amount with dot thousands separators. */
export function vnd(n: number | null | undefined, suffix = " đ"): string {
  if (n === null || n === undefined || Number.isNaN(n)) return "";
  return Math.round(n).toLocaleString("de-DE") + suffix;
}

/** Route builders — keep in sync with docs/03-san-pham/pages.md. */
export const productHref = (slug: string) => `/products/${slug}`;
export const categoryHref = (slug: string) => `/categories/${slug}`;
export const brandHref = (slug: string) => `/brands/${slug}`;
export const articleHref = (slug: string) => `/blog/${slug}`;
export const journalHref = (slug: string) => `/journal/${slug}`;
export const infoPageHref = (slug: string) => `/pages/${slug}`;

/** Compact counts like the PDP social proof block ("1.3K", "18k"). */
export function compact(n: number, upper = true): string {
  if (n >= 1000) {
    const v = (n / 1000).toFixed(1).replace(/\.0$/, "");
    return v + (upper ? "K" : "k");
  }
  return String(n);
}
