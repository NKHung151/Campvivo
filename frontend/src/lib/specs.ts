/** Pull key/value rows out of the "Đặc tính" tab HTML (tables of <td>key</td><td>value</td>). */
export function parseSpecs(html: string | null | undefined): [string, string][] {
  if (!html) return [];
  const text = (s: string) =>
    s
      .replace(/<[^>]+>/g, " ")
      .replace(/&nbsp;/g, " ")
      .replace(/&amp;/g, "&")
      .replace(/\s+/g, " ")
      .trim();
  const rows: [string, string][] = [];
  for (const tr of html.match(/<tr[\s\S]*?<\/tr>/gi) ?? []) {
    const cells = (tr.match(/<t[dh][^>]*>[\s\S]*?<\/t[dh]>/gi) ?? []).map(text).filter(Boolean);
    if (cells.length >= 2 && cells[0].length < 60) rows.push([cells[0].replace(/:$/, ""), cells.slice(1).join(" ")]);
  }
  if (rows.length) return rows;
  for (const li of html.match(/<li[\s\S]*?<\/li>/gi) ?? []) {
    const t = text(li);
    const i = t.indexOf(":");
    if (i > 0 && i < 50) rows.push([t.slice(0, i).trim(), t.slice(i + 1).trim()]);
  }
  return rows;
}
