"use client";

import { useState } from "react";
import type { InfoPage as Info, LinkItem } from "@/types/shop";
import { SmartLink } from "@/components/ui/SmartLink";
import { infoPageHref } from "@/lib/format";

// "Các bài viết khác" links may carry an id prefix (/397/tnd/...); normalise them to /pages/<slug>.
const tndHref = (h: string) => h.replace(/^\/\d+\/tnd\/([^/]+)\.htm$/, "/pages/$1");

export function InfoPageView({ page, nav }: { page: Info; nav: LinkItem[] }) {
  const [size, setSize] = useState<number | null>(null);
  return (
    <div className="pageContent">
      <div>
        <div id="Breadcrumb">
          <div className="road">
            <SmartLink className="lv1" href="/" title="Trang chủ">
              Trang chủ
            </SmartLink>{" "}
            <SmartLink className="lv1 arrow" href={infoPageHref(page.slug)} title={page.title}>
              {page.title}
            </SmartLink>
          </div>
        </div>
        <div id="ctl00_ctl01_TNDIndex1_pnSingleItem">
          <div className="col1">
            <div className="tndCategories">
              {nav.map((n) => (
                <SmartLink key={n.href} href={n.href} title={n.name} className={n.href === infoPageHref(page.slug) ? "current" : "nm"}>
                  {n.name}
                </SmartLink>
              ))}
            </div>
          </div>
          <div id="TNDDetail" className="col2">
            <h1 className="title">{page.title}</h1>
            <div className="tool">
              <div className="date">{page.updated}</div>
              <div className="view">{page.views}</div>
              <div className="size">
                <a className="NormalSize" href="#" onClick={(e) => (e.preventDefault(), setSize(null))} title="Click để chuyển về cỡ chữ mặc định">
                  Cỡ chữ
                </a>
                &nbsp;{" "}
                <a className="SmallSize" href="#" onClick={(e) => (e.preventDefault(), setSize((s) => Math.max(12, (s ?? 16) - 1)))} title="Click để giảm cỡ chữ">
                  &nbsp;
                </a>{" "}
                <a className="LargeSize" href="#" onClick={(e) => (e.preventDefault(), setSize((s) => Math.min(24, (s ?? 16) + 1)))} title="Click để tăng cỡ chữ">
                  &nbsp;
                </a>
              </div>
              <div className="cb" />
            </div>
            <div className="contentview TextSize" style={size ? { fontSize: size } : undefined} dangerouslySetInnerHTML={{ __html: page.html }} />
            {page.others.length > 0 && <div className="titleo">Các bài viết khác</div>}
            {page.others.map((o) => (
              <div key={o.href}>
                <SmartLink className="o" href={tndHref(o.href)} title={o.name}>
                  {o.name} <span>{o.extra}</span>
                </SmartLink>
                <div className="vieno" />
              </div>
            ))}
          </div>
          <div className="cb h20" />
        </div>
      </div>
    </div>
  );
}
