"use client";

import { Children, useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";

/*
 * Minimal re-implementation of the Owl Carousel 2 behaviour campvivo.vn uses.
 * It renders Owl's DOM/class names so the carousel CSS in styles/shop applies unchanged.
 */
interface Props {
  className?: string;
  items?: number;
  /** Responsive overrides: min viewport width -> items. */
  responsive?: Record<number, number>;
  margin?: number;
  nav?: boolean;
  dots?: boolean;
  loop?: boolean;
  autoplay?: number;
  autoWidth?: boolean;
  navText?: [React.ReactNode, React.ReactNode];
  children: React.ReactNode;
}

const useIsoLayoutEffect = typeof window === "undefined" ? useEffect : useLayoutEffect;

export function OwlCarousel({
  className = "",
  items = 1,
  responsive,
  margin = 0,
  nav = false,
  dots = true,
  loop = false,
  autoplay,
  autoWidth = false,
  navText = ["‹", "›"],
  children,
}: Props) {
  const slides = Children.toArray(children);
  const outerRef = useRef<HTMLDivElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);
  const [outerW, setOuterW] = useState(0);
  const [perView, setPerView] = useState(items);
  const [index, setIndex] = useState(0);
  const [offsets, setOffsets] = useState<number[]>([]);
  const [hover, setHover] = useState(false);
  const drag = useRef<{ x: number; dx: number } | null>(null);
  const [dragDx, setDragDx] = useState(0);

  useIsoLayoutEffect(() => {
    const el = outerRef.current;
    if (!el) return;
    const measure = () => {
      setOuterW(el.clientWidth);
      if (responsive) {
        const vw = window.innerWidth;
        let n = items;
        for (const bp of Object.keys(responsive).map(Number).sort((a, b) => a - b)) if (vw >= bp) n = responsive[bp];
        setPerView(n);
      }
    };
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, [items, responsive]);

  const itemW = autoWidth ? 0 : outerW ? (outerW + margin) / perView - margin : 0;

  // autoWidth: measure natural widths for offsets
  useIsoLayoutEffect(() => {
    if (!autoWidth || !stageRef.current) return;
    const kids = Array.from(stageRef.current.children) as HTMLElement[];
    let acc = 0;
    const offs = kids.map((k) => {
      const o = acc;
      acc += k.offsetWidth + margin;
      return o;
    });
    setOffsets(offs);
  }, [autoWidth, outerW, slides.length, margin]);

  const maxIndex = autoWidth
    ? Math.max(0, offsets.findIndex((o) => o + outerW >= (offsets.at(-1) ?? 0) + 200))
    : Math.max(0, slides.length - perView);
  const pages = autoWidth ? Math.max(1, maxIndex + 1) : Math.max(1, Math.ceil(slides.length / perView));

  const go = useCallback(
    (i: number) => {
      if (loop) setIndex(((i % (maxIndex + 1)) + (maxIndex + 1)) % (maxIndex + 1));
      else setIndex(Math.max(0, Math.min(maxIndex, i)));
    },
    [loop, maxIndex],
  );

  useEffect(() => {
    if (!autoplay || hover || slides.length <= perView) return;
    const t = setInterval(() => setIndex((i) => (i >= maxIndex ? 0 : i + 1)), autoplay);
    return () => clearInterval(t);
  }, [autoplay, hover, maxIndex, perView, slides.length]);

  const clamped = Math.min(index, maxIndex);
  const translate = autoWidth ? offsets[clamped] ?? 0 : clamped * (itemW + margin);
  const stageW = autoWidth ? undefined : slides.length * (itemW + margin);
  const activePage = autoWidth ? clamped : Math.min(pages - 1, Math.round(clamped / perView));

  const onPointerDown = (e: React.PointerEvent) => {
    drag.current = { x: e.clientX, dx: 0 };
  };
  const onPointerMove = (e: React.PointerEvent) => {
    if (!drag.current) return;
    drag.current.dx = e.clientX - drag.current.x;
    if (Math.abs(drag.current.dx) > 5) setDragDx(drag.current.dx);
  };
  const endDrag = () => {
    if (!drag.current) return;
    const dx = drag.current.dx;
    drag.current = null;
    setDragDx(0);
    const step = autoWidth ? 120 : itemW + margin || 1;
    if (Math.abs(dx) > 40) go(clamped - Math.round(dx / step) || clamped + (dx < 0 ? 1 : -1));
  };

  return (
    <div
      className={`${className} owl-carousel owl-theme owl-loaded owl-drag`}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
    >
      <div
        className="owl-stage-outer"
        ref={outerRef}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={endDrag}
        onPointerLeave={endDrag}
        onClickCapture={(e) => {
          if (dragDx) e.preventDefault();
        }}
      >
        <div
          ref={stageRef}
          className="owl-stage"
          style={{
            transform: `translate3d(${-translate + dragDx}px, 0px, 0px)`,
            transition: dragDx ? "none" : "all 0.25s ease 0s",
            width: stageW ? Math.ceil(stageW) : 99999,
          }}
        >
          {slides.map((s, i) => (
            <div
              key={i}
              className={`owl-item${i >= clamped && i < clamped + perView ? " active" : ""}`}
              style={{ width: autoWidth ? "auto" : itemW || `${100 / perView}%`, marginRight: margin }}
            >
              {s}
            </div>
          ))}
        </div>
      </div>
      {nav && (
        <div className="owl-nav">
          <button type="button" role="presentation" className={`owl-prev${!loop && clamped === 0 ? " disabled" : ""}`} onClick={() => go(clamped - 1)}>
            <span aria-label="Previous">{navText[0]}</span>
          </button>
          <button
            type="button"
            role="presentation"
            className={`owl-next${!loop && clamped >= maxIndex ? " disabled" : ""}`}
            onClick={() => go(clamped + 1)}
          >
            <span aria-label="Next">{navText[1]}</span>
          </button>
        </div>
      )}
      {dots && pages > 1 && (
        <div className="owl-dots">
          {Array.from({ length: pages }, (_, p) => (
            <button
              key={p}
              type="button"
              role="button"
              className={`owl-dot${p === activePage ? " active" : ""}`}
              onClick={() => go(autoWidth ? p : p * perView)}
            >
              <span />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
