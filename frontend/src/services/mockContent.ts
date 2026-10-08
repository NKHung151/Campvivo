/**
 * The mock catalogue was collected from a third-party reference store for UI testing.
 * Its copy names that store (brand, hotline), so before rendering we:
 *  - drop the store's tags from titles ("[XxxOlogy] …", "… | XxxOlogy");
 *  - hide short texts (excerpts, intros, card descriptions) that still mention it;
 *  - swap long bodies (articles, product descriptions, policies) that mention it for a
 *    "đang cập nhật" placeholder instead of re-attributing someone else's writing.
 * Content written by the team never matches, so this becomes a no-op once real data lands.
 */

const MENTION = /we\s?trek|0?28\s?73\s?05\s?1988|02873051988|0287\s?305\s?1988/i;
const TAG_BRACKET = /\s*\[\s*we\s?trek[a-z]*\s*\]\s*/gi;
const TAG_SUFFIX = /\s*[|–-]\s*we\s?trek[a-z]*\s*(?=$|<|")/gi;
const PHRASE = /\s*(?:tại|của|cùng|ở|với|từ|bởi|về)?\s*we\s?trek[a-z.]*/gi;
const HOTLINE = /0?28\s?73\s?05\s?1988|02873051988|0287\s?305\s?1988/g;

export const PLACEHOLDER_HTML = '<p class="cv-placeholder">Nội dung đang được cập nhật.</p>';

/** Long-form HTML that gets the placeholder; other HTML fragments are simply dropped. */
const BODY_KEYS = new Set(["html", "descriptionHtml", "specsHtml", "policyHtml", "seoHtml"]);
/** Short texts that are hidden rather than edited. */
const HIDE_KEYS = new Set(["excerpt", "intro", "desc", "short", "text", "guide", "storesTitle"]);
/** Never touched: identifiers, links, media. */
const SKIP_KEYS = new Set(["slug", "href", "img", "src", "big", "thumb", "url", "video", "cls", "sku", "key", "banner"]);

/** Ignore URLs (image paths may legitimately contain the old host). */
const visible = (s: string) => s.replace(/(?:https?:)?\/\/[^\s"'<>)\\]+/g, "");
const mentions = (s: string) => MENTION.test(visible(s));
const isHtml = (s: string) => /<[a-z!][^>]*>/i.test(s);

function cleanTitle(s: string): string {
  return s.replace(TAG_BRACKET, " ").replace(TAG_SUFFIX, "").replace(/\s{2,}/g, " ").trim();
}

/** Same rule as plain titles, for headings and alt/title attributes inside HTML. */
function cleanInlineTitle(s: string): string {
  const t = cleanTitle(s);
  return mentions(t) ? t.replace(PHRASE, "").replace(HOTLINE, "").replace(/\s{2,}/g, " ").trim() : t;
}

function cleanHtml(html: string, key: string): string {
  let h = html.replace(TAG_BRACKET, " ").replace(TAG_SUFFIX, "");
  h = h.replace(/(<h[1-6][^>]*>)([^<]*)(<\/h[1-6]>)/g, (_, a: string, t: string, b: string) => a + cleanInlineTitle(t) + b);
  h = h.replace(/\b(alt|title)="([^"]*)"/g, (_, attr: string, v: string) => `${attr}="${cleanInlineTitle(v)}"`);
  // card descriptions inside listing HTML
  h = h.replace(/<p class="desc">[\s\S]*?<\/p>/g, (p) => (mentions(p) ? "" : p));
  if (!mentions(h)) return h;
  return BODY_KEYS.has(key) ? PLACEHOLDER_HTML : "";
}

function cleanString(s: string, key: string): string {
  if (SKIP_KEYS.has(key) || s.startsWith("/") || s.startsWith("http")) return s;
  if (!/we\s?trek|28\s?73|873051988/i.test(s)) return s;
  if (isHtml(s)) return cleanHtml(s, key);
  const t = cleanTitle(s);
  if (!mentions(t)) return t;
  if (HIDE_KEYS.has(key)) return "";
  return t.replace(PHRASE, "").replace(HOTLINE, "").replace(/\s{2,}/g, " ").trim();
}

/** Deep-clean a parsed mock JSON value. */
export function cleanMock<T>(value: T, key = ""): T {
  if (typeof value === "string") return cleanString(value, key) as T;
  if (Array.isArray(value)) return value.map((v) => cleanMock(v, key)) as T;
  if (value && typeof value === "object") {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      // map keys that are slugs (categories, products...) keep the parent field name
      out[k] = cleanMock(v, /^[a-z0-9_-]+$/.test(k) && k.includes("-") ? key : k);
    }
    return out as T;
  }
  return value;
}
