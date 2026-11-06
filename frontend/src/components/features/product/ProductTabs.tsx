"use client";

import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ReviewSection } from "@/components/features/review/ReviewSection";

const TABS = [
  { id: "tabcontent1", label: "Chi tiết" },
  { id: "tabcontent2", label: "Đặc tính" },
  { id: "tabcontent3", label: "Hướng dẫn" },
  { id: "tabcontent4", label: "Đánh giá" },
];

/** Khoảng cách thêm dưới thanh tab khi cuộn tới một mục. */
const GAP = 8;

/**
 * Khối HTML của một mục. Tách riêng + memo để đổi tab đang sáng (re-render ProductTabs) không gán lại innerHTML:
 * gán lại làm ảnh trong mô tả bị tạo lại, co về 0px rồi nở ra → trang giật khi cuộn qua ranh giới các mục.
 */
const HtmlPanel = memo(function HtmlPanel({ id, className, html }: { id: string; className: string; html: string }) {
  return <div id={id} className={className} dangerouslySetInnerHTML={{ __html: html }} />;
});

/**
 * campvivo.vn renders all four tab panels one after another under a sticky tab bar;
 * tabs scroll to their panel and highlight as you scroll.
 * - Bấm tab: sáng ngay tab đó, cuộn tới mục (xa thì nhảy gần tới rồi trượt đoạn cuối cho nhanh).
 * - Người dùng tự cuộn/chạm/bấm phím trong lúc đang trượt → dừng trượt ngay, không bị kéo ngược lại.
 * - Cuộn tay: tab sáng theo mục đang nằm ngay dưới thanh tab.
 * - Link `#tabcontentN` ở chỗ khác trên trang (xem đánh giá, xem thông số…) dùng chung cách cuộn này.
 * - Mục "Đánh giá" là khối đánh giá thật (FR-18, `ReviewSection`).
 */
export function ProductTabs({
  productId,
  descriptionHtml,
  specsHtml,
  guideHtml,
}: {
  productId: number;
  descriptionHtml: string | null;
  specsHtml: string | null;
  guideHtml: string | null;
}) {
  const [current, setCurrent] = useState("tabcontent1");
  const barRef = useRef<HTMLDivElement>(null);
  /** Đang trượt tới mục vừa bấm — tạm không đổi tab theo vị trí cuộn. */
  const autoScrolling = useRef(false);
  const settleTimer = useRef<ReturnType<typeof setTimeout>>();
  /** Mục đang trượt tới + số lần đã chỉnh lại (ảnh phía trên tải xong làm lệch đích). */
  const pending = useRef<{ id: string; fixes: number } | null>(null);
  // Ảnh trong mô tả tải ngay khi mở trang (không lazy): ảnh dữ liệu cũ không có kích thước, tải trễ sẽ nở ra
  // hàng nghìn px lúc đang cuộn ngang qua → đẩy nội dung, làm giật trang.
  const description = useMemo(() => (descriptionHtml ?? "").replace(/\sloading=(["'])lazy\1/gi, ""), [descriptionHtml]);

  /** Vị trí cuộn để đầu mục nằm ngay dưới thanh tab. */
  const targetOf = useCallback(
    (el: HTMLElement) => Math.max(0, el.getBoundingClientRect().top + window.scrollY - (barRef.current?.offsetHeight ?? 0) - GAP),
    [],
  );

  /** Hết trượt: nếu bố cục đã đổi làm lệch đích thì trượt bù (tối đa 3 lần), không thì thôi. */
  const settle = useCallback(() => {
    const p = pending.current;
    const el = p ? document.getElementById(p.id) : null;
    if (p && el && p.fixes < 3) {
      const target = targetOf(el);
      if (Math.abs(target - window.scrollY) > 4 && window.innerHeight + window.scrollY < document.documentElement.scrollHeight - 2) {
        p.fixes++;
        window.scrollTo({ top: target, behavior: "smooth" });
        clearTimeout(settleTimer.current);
        settleTimer.current = setTimeout(settle, 600);
        return;
      }
    }
    pending.current = null;
    autoScrolling.current = false;
  }, [targetOf]);

  /** Mục đang nằm ngay dưới thanh tab (cuối trang thì là mục cuối). */
  const sectionAtTop = useCallback(() => {
    const line = (barRef.current?.getBoundingClientRect().bottom ?? 0) + GAP + 2;
    const present = TABS.map((t) => document.getElementById(t.id)).filter((el): el is HTMLElement => !!el);
    if (!present.length) return TABS[0].id;
    if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2) return present[present.length - 1].id;
    let cur = present[0].id;
    for (const el of present) if (el.getBoundingClientRect().top <= line) cur = el.id;
    return cur;
  }, []);

  useEffect(() => {
    let frame = 0;
    const onScroll = () => {
      if (autoScrolling.current) {
        // Hết trượt khi không còn sự kiện cuộn trong 120ms
        clearTimeout(settleTimer.current);
        settleTimer.current = setTimeout(settle, 120);
        return;
      }
      if (frame) return;
      frame = requestAnimationFrame(() => {
        frame = 0;
        setCurrent(sectionAtTop());
      });
    };
    // Người dùng tự cuộn trong lúc đang trượt → dừng trượt tại chỗ
    const stopAuto = () => {
      if (!autoScrolling.current) return;
      autoScrolling.current = false;
      pending.current = null;
      clearTimeout(settleTimer.current);
      window.scrollTo({ top: window.scrollY, behavior: "instant" as ScrollBehavior });
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    for (const t of ["wheel", "touchstart", "mousedown", "keydown"] as const) window.addEventListener(t, stopAuto, { passive: true });
    return () => {
      window.removeEventListener("scroll", onScroll);
      for (const t of ["wheel", "touchstart", "mousedown", "keydown"] as const) window.removeEventListener(t, stopAuto);
      cancelAnimationFrame(frame);
      clearTimeout(settleTimer.current);
    };
  }, [sectionAtTop, settle]);

  const go = useCallback((id: string) => {
    const el = document.getElementById(id);
    if (!el) return;
    const target = targetOf(el);
    setCurrent(id);
    autoScrolling.current = true;
    pending.current = { id, fixes: 0 };
    // Xa hơn ~1,5 màn hình: nhảy tới gần rồi mới trượt, để không trượt dài qua cả trang
    const near = window.innerHeight * 1.5;
    const distance = target - window.scrollY;
    if (Math.abs(distance) > near) window.scrollTo({ top: target - Math.sign(distance) * near, behavior: "instant" as ScrollBehavior });
    window.scrollTo({ top: target, behavior: "smooth" });
    clearTimeout(settleTimer.current);
    settleTimer.current = setTimeout(settle, 1000);
  }, [settle, targetOf]);

  // Link "#tabcontentN" ở chỗ khác trên trang (xem đánh giá, xem thông số…) cũng cuộn bằng go()
  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      const a = (e.target as Element | null)?.closest?.('a[href^="#tabcontent"]');
      const id = a?.getAttribute("href")?.slice(1);
      if (!id) return;
      e.preventDefault();
      go(id);
    };
    document.addEventListener("click", onClick);
    return () => document.removeEventListener("click", onClick);
  }, [go]);

  return (
    <div id="SubProductTabInfo">
      <div className="bgTab" ref={barRef}>
        {TABS.map((t, i) => (
          <a key={t.id} id={`tabdetail${i + 1}`} className={`tab${current === t.id ? " current" : ""}`} href={`#${t.id}`}>
            {t.label}
            <span className="arrow" />
          </a>
        ))}
      </div>
      <HtmlPanel id="tabcontent1" className="tabcontent contentview" html={description} />
      {specsHtml && <HtmlPanel id="tabcontent2" className="tabcontent" html={specsHtml} />}
      {guideHtml && <HtmlPanel id="tabcontent3" className="tabcontent" html={guideHtml} />}
      <div id="tabcontent4" className="tabcontent">
        <ReviewSection itemType="PRODUCT" itemId={productId} />
      </div>
    </div>
  );
}
