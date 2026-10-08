"use client";
/* eslint-disable @next/next/no-img-element */

import { useLayoutEffect, useRef } from "react";
import { SmartLink } from "@/components/ui/SmartLink";
import { compact } from "@/lib/format";
import type { FbtItem } from "@/types/product";

const Star = () => (
  <svg width="13" height="13" viewBox="0 0 24 24" fill="#f5a623" aria-hidden="true">
    <path d="M12 2l3.1 6.3 6.9 1-5 4.9 1.2 6.8L12 17.8 5.8 21l1.2-6.8-5-4.9 6.9-1z" />
  </svg>
);
const Arrow = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
    <path d="M5 12h14M13 6l6 6-6 6" />
  </svg>
);

export interface PdpDynProps {
  journeys: { icon: string; title: string; sub: string }[];
  heroImg: string | null;
  tips: string[];
  specs: [string, string][];
  articles: { title: string; url: string }[];
  social: { sold: number; rating: number; satisf: number } | null;
  fbt: FbtItem[];
  reviews: { name: string; rating: number; text: string }[];
}

/** "CHỌN ĐÚNG TRƯỚC KHI MUA" — rebuilt from the data campvivo.vn's pdp-dyn.js renders client-side. */
export function PdpDyn({ journeys, heroImg, tips, specs, articles, social, fbt, reviews }: PdpDynProps) {
  const gridRef = useRef<HTMLDivElement>(null);

  // Masonry layout: 4px auto-rows + 14px gap, span computed from content height.
  useLayoutEffect(() => {
    const grid = gridRef.current;
    if (!grid) return;
    const layout = () => {
      const mods = Array.from(grid.children) as HTMLElement[];
      mods.forEach((m) => (m.style.gridRowEnd = ""));
      mods.forEach((m) => {
        const h = m.getBoundingClientRect().height;
        m.style.gridRowEnd = `span ${Math.ceil((h + 14) / 18)}`;
      });
    };
    layout();
    const ro = new ResizeObserver(layout);
    ro.observe(grid);
    return () => ro.disconnect();
  }, []);

  const hero = journeys[0];
  return (
    <div id="pdp-dyn" className="pdp-dyn pdp-rank-1" role="region" aria-labelledby="pdp-dyn-h2">
      <div className="pdp-dyn-head">
        <h2 className="pdp-dyn-h2" id="pdp-dyn-h2">
          CHỌN ĐÚNG TRƯỚC KHI MUA
        </h2>
        <p className="pdp-dyn-sub2">Kiến thức Campvivology, hành trình phù hợp &amp; so sánh trong tầm giá.</p>
      </div>
      <div className="pdp-dyn-state">
        <div className="pdp-dyn-state-sub">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
            <path d="M9 18h6M10 21h4M12 3a6 6 0 00-3.5 10.9V16h7v-2.1A6 6 0 0012 3z" />
          </svg>{" "}
          Gợi ý: Xem bảng hướng dẫn chọn size và trường hợp sử dụng bên dưới
        </div>
      </div>
      <div className="pdp-dyn-grid pdp-dyn-grid-masonry" ref={gridRef}>
        <div className="pdp-dyn-mod" data-mod="M1">
          <div
            className="pdp-dyn-uc"
            style={{
              backgroundImage: `linear-gradient(135deg,rgba(1,74,36,.82),rgba(2,102,50,.55))${heroImg ? `,url('${heroImg}')` : ""}`,
              backgroundSize: "cover",
              backgroundPosition: "center",
            }}
          >
            <div className="pdp-dyn-uc-ghost">THE JOURNEY</div>
            <span className="pdp-dyn-uc-tag">Lần đầu đến Campvivo? Bắt đầu đúng ngay đây</span>
            <div className="pdp-dyn-uc-tx">
              <b>{hero.title}</b>
              <span>{hero.sub}</span>
            </div>
          </div>
          <div className="pdp-dyn-uc-chips">
            {journeys.map((j) => (
              <i key={j.title}>{j.title}</i>
            ))}
          </div>
        </div>
        <div className="pdp-dyn-mod" data-mod="M4">
          <div className="pdp-dyn-mod-hd">
            <h3 className="pdp-dyn-mod-title">Kiến thức &amp; chọn đúng</h3>
            <span className="pdp-dyn-more">Campvivology</span>
          </div>
          <div className="pdp-dyn-edu-tips">
            {tips.map((t) => (
              <div className="pdp-dyn-edu-tip" key={t}>
                {t}
              </div>
            ))}
          </div>
          {specs.length > 0 && (
            <>
              <div className="pdp-dyn-edu-sub">🔍 Đặc Tính Nổi Bật</div>
              <div className="pdp-dyn-edu-specs">
                {specs.slice(0, 4).map(([k, v]) => (
                  <div className="pdp-dyn-edu-spec-row" key={k}>
                    ✓ {k}: {v}
                  </div>
                ))}
              </div>
            </>
          )}
          {articles.length > 0 && (
            <>
              <div className="pdp-dyn-edu-sub">📰 Bài Viết Liên Quan</div>
              <div className="pdp-dyn-articles">
                {articles.map((a) => (
                  <SmartLink className="pdp-dyn-article-row" href={a.url} key={a.url}>
                    <span className="pdp-dyn-article-title">{a.title}</span>
                    <span className="pdp-dyn-article-arrow">
                      <Arrow />
                    </span>
                  </SmartLink>
                ))}
              </div>
            </>
          )}
          <div className="pdp-dyn-size-link">
            <a href={specs.length ? "#tabcontent2" : "#tabcontent3"} role="button">
              {specs.length ? "Xem đầy đủ đặc tính" : "Xem hướng dẫn sử dụng"} <Arrow />
            </a>
          </div>
        </div>
        {social && (
          <div className="pdp-dyn-mod" data-mod="M2">
            <div className="pdp-dyn-mod-hd">
              <h3 className="pdp-dyn-mod-title">Đánh giá cộng đồng</h3>
            </div>
            <div className="pdp-dyn-social">
              <div className="pdp-dyn-social-stat">
                <div className="pdp-dyn-social-num">{compact(social.sold)}</div>
                <div className="pdp-dyn-social-label">Đã mua</div>
              </div>
              <div className="pdp-dyn-social-stat">
                <div className="pdp-dyn-social-num">
                  {social.rating}
                  <Star />
                </div>
                <div className="pdp-dyn-social-label">Điểm trung bình</div>
              </div>
              <div className="pdp-dyn-social-stat">
                <div className="pdp-dyn-social-num">{social.satisf}%</div>
                <div className="pdp-dyn-social-label">Hài lòng</div>
              </div>
            </div>
          </div>
        )}
        {fbt.length > 0 && (
          <div className="pdp-dyn-mod" data-mod="M5">
            <div className="pdp-dyn-mod-hd">
              <h3 className="pdp-dyn-mod-title">Thường được mua cùng</h3>
            </div>
            <div className="pdp-dyn-cards">
              {fbt.slice(0, 3).map((f) => (
                <SmartLink className="pdp-dyn-card" href={f.link} key={f.link}>
                  <div className="pdp-dyn-card-imgwrap">
                    <img className="pdp-dyn-card-img" src={f.img} alt="" loading="lazy" />
                    {f.disc && <span className="pdp-dyn-card-badge">{f.disc}</span>}
                  </div>
                  <div className="pdp-dyn-card-body">
                    <div className="pdp-dyn-card-name">{f.name}</div>
                    <div className="pdp-dyn-card-price">{f.price.toLocaleString("de-DE")}đ</div>
                    <div className="pdp-dyn-card-meta">
                      {f.rating > 0 && (
                        <>
                          <Star />
                          {f.rating}
                        </>
                      )}
                      {f.sold > 0 && ` · đã bán ${compact(f.sold, false)}`}
                    </div>
                  </div>
                </SmartLink>
              ))}
            </div>
          </div>
        )}
        {reviews.length > 0 && (
          <div className="pdp-dyn-mod" data-mod="M7">
            <div className="pdp-dyn-mod-hd">
              <h3 className="pdp-dyn-mod-title">Trải nghiệm thực tế</h3>
            </div>
            <div className="pdp-dyn-testimonials">
              {reviews.slice(0, 3).map((r, i) => (
                <div className="pdp-dyn-testimonial-row" key={i}>
                  <div className="pdp-dyn-testimonial-avatar">{r.name.charAt(0).toUpperCase()}</div>
                  <div className="pdp-dyn-testimonial-body">
                    <div className="pdp-dyn-testimonial-name">{r.name}</div>
                    <div className="pdp-dyn-testimonial-stars">
                      <Star />
                    </div>
                    <div className="pdp-dyn-testimonial-text">{r.text}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
      <a className="pdp-dyn-cta" href="#tabcontent1" role="button">
        TÌM HIỂU THÊM <Arrow />
      </a>
    </div>
  );
}
