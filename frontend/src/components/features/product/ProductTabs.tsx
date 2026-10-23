"use client";

import { useEffect, useState } from "react";
import type { Review } from "@/types/shop";
import { useShop } from "@/components/features/shop/ShopProvider";

const TABS = [
  { id: "tabcontent1", label: "Chi tiết" },
  { id: "tabcontent2", label: "Đặc tính" },
  { id: "tabcontent3", label: "Hướng dẫn" },
  { id: "tabcontent4", label: "Đánh giá" },
];

/**
 * campvivo.vn renders all four tab panels one after another under a sticky tab bar;
 * tabs scroll to their panel and highlight as you scroll.
 */
export function ProductTabs({
  slug,
  descriptionHtml,
  specsHtml,
  guideHtml,
  rating,
  reviews,
}: {
  slug: string;
  descriptionHtml: string | null;
  specsHtml: string | null;
  guideHtml: string | null;
  rating: number;
  reviews: Review[];
}) {
  const [current, setCurrent] = useState("tabcontent1");
  const { notify } = useShop();
  const [local, setLocal] = useState<Review[]>([]);
  const [stars, setStars] = useState(5);
  const [text, setText] = useState("");

  useEffect(() => {
    const ids = TABS.map((t) => t.id);
    const onScroll = () => {
      let cur = ids[0];
      for (const id of ids) {
        const el = document.getElementById(id);
        if (el && el.getBoundingClientRect().top < 120) cur = id;
      }
      setCurrent(cur);
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    try {
      setLocal(JSON.parse(window.localStorage.getItem(`cv-reviews:${slug}`) ?? "[]") as Review[]);
    } catch {
      /* ignore */
    }
  }, [slug]);

  const all = [...local, ...reviews];
  const go = (id: string) => {
    const el = document.getElementById(id);
    if (el) window.scrollTo({ top: el.getBoundingClientRect().top + window.scrollY - 50, behavior: "smooth" });
  };

  return (
    <div id="SubProductTabInfo">
      <div className="bgTab">
        {TABS.map((t, i) => (
          <a
            key={t.id}
            id={`tabdetail${i + 1}`}
            className={`tab${current === t.id ? " current" : ""}`}
            href={`#${t.id}`}
            onClick={(e) => {
              e.preventDefault();
              go(t.id);
            }}
          >
            {t.label}
            <span className="arrow" />
          </a>
        ))}
      </div>
      <div id="tabcontent1" className="tabcontent contentview" dangerouslySetInnerHTML={{ __html: descriptionHtml ?? "" }} />
      {specsHtml && <div id="tabcontent2" className="tabcontent" dangerouslySetInnerHTML={{ __html: specsHtml }} />}
      {guideHtml && <div id="tabcontent3" className="tabcontent" dangerouslySetInnerHTML={{ __html: guideHtml }} />}
      <div id="tabcontent4" className="tabcontent">
        <div id="SubProductComments">
          <div className="header">Đánh giá của người dùng</div>
          <div className="product_ratings">
            <div className="core">
              <div className="stars" style={{ width: `${rating || 100}%` }} />
            </div>
            <span className="text-danger">{((rating || 100) / 20).toFixed(0)}</span>&nbsp;điểm&nbsp;/&nbsp;<span className="text-danger">{all.length}</span>&nbsp;đánh giá.
          </div>
          <div className="count" />
          <div className="listComment">
            {all.map((r, i) => (
              <div key={i}>
                <div className="comment">
                  <div>
                    <span className="ctext fwb">{r.name}</span> -{" "}
                    <span className="date">
                      <span title={r.date}>{r.date}</span>
                    </span>{" "}
                    <br />
                    <div className="core">
                      <div className="stars" style={{ width: `${r.rating * 20}%` }} />
                    </div>
                    <div>{r.text}</div>
                  </div>
                  <div className="cb" />
                  <div className="cb h5" />
                </div>
                {i < all.length - 1 && <div className="vien" />}
              </div>
            ))}
          </div>
          <div className="cb h10" />
          <form
            id="BoxComment"
            className="boxcontent"
            onSubmit={(e) => {
              e.preventDefault();
              if (text.trim().length < 16) {
                notify("Bình luận cần tối thiểu 16 ký tự");
                return;
              }
              const r: Review = { name: "Bạn", date: new Date().toLocaleString("vi-VN"), rating: stars, text: text.trim() };
              const next = [r, ...local];
              setLocal(next);
              setText("");
              try {
                window.localStorage.setItem(`cv-reviews:${slug}`, JSON.stringify(next));
              } catch {
                /* ignore */
              }
              notify("Cảm ơn bạn đã đánh giá sản phẩm!");
            }}
          >
            <div className="pb5"> Đánh giá sảm phẩm này </div>
            <div className="saoxam" id="DanhGiaChiTietSP">
              {[1, 2, 3, 4, 5].reduceRight<React.ReactNode>(
                (inner, n) => (
                  <span
                    id={`Rate_${n}`}
                    className={n <= stars ? "current" : ""}
                    onClick={(e) => {
                      e.stopPropagation();
                      setStars(n);
                    }}
                  >
                    {inner}
                  </span>
                ),
                <>&nbsp;</>,
              )}
            </div>
            <div className="cb pt15 pb3"> Bình luận (*): </div>
            <textarea
              id="tbContent"
              style={{ width: "100%", padding: 10, boxSizing: "border-box" }}
              rows={5}
              minLength={16}
              maxLength={1020}
              value={text}
              onChange={(e) => setText(e.target.value)}
            />
            <div className="cb h20" />
            <div className="tac">
              <button type="submit" className="btOK">
                Gửi bình luận
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
