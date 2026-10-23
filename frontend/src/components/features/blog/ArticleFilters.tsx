"use client";

import { useState } from "react";
import { SmartLink } from "@/components/ui/SmartLink";

/**
 * Left column of the article list. Category links navigate; the checkbox groups
 * (Loại bài / Đối tượng / Chủ đề HOT) will filter server-side once the blog API exists; for now they only toggle.
 */
export function ArticleFilters({ filters }: { filters: { title: string; options: { name: string; href: string | null }[] }[] }) {
  const [checked, setChecked] = useState<string[]>([]);
  return (
    <div className="tour_col_l">
      {filters.map((f, fi) => (
        <div className="filterFrame" key={f.title}>
          <div className="top">{f.title}</div>
          <ul>
            {f.options.map((o, i) =>
              o.href ? (
                <li key={o.name}>
                  <SmartLink className="cate" href={o.href} title={o.name}>
                    {o.name}
                  </SmartLink>
                </li>
              ) : (
                <li key={o.name}>
                  <input
                    type="checkbox"
                    id={`cb${fi}-${i}`}
                    checked={checked.includes(o.name)}
                    onChange={() => setChecked((c) => (c.includes(o.name) ? c.filter((x) => x !== o.name) : [...c, o.name]))}
                  />
                  <label htmlFor={`cb${fi}-${i}`}>
                    <span>{o.name}</span>
                  </label>
                </li>
              ),
            )}
          </ul>
        </div>
      ))}
    </div>
  );
}
