"use client";
/* eslint-disable @next/next/no-img-element */

import { useEffect } from "react";

/** Replacement for the fancybox overlay campvivo.vn uses for galleries and videos. */
export function Lightbox({
  onClose,
  children,
  images,
  index = 0,
  onIndex,
}: {
  onClose: () => void;
  children?: React.ReactNode;
  images?: { src: string; alt: string }[];
  index?: number;
  onIndex?: (i: number) => void;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
      if (images && onIndex) {
        if (e.key === "ArrowRight") onIndex((index + 1) % images.length);
        if (e.key === "ArrowLeft") onIndex((index - 1 + images.length) % images.length);
      }
    };
    window.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = prev;
    };
  }, [onClose, images, index, onIndex]);

  return (
    <div className="cv-lightbox" onClick={onClose}>
      <div onClick={(e) => e.stopPropagation()}>
        {images ? <img src={images[index].src} alt={images[index].alt} /> : children}
      </div>
      {images && images.length > 1 && onIndex && (
        <>
          <span className="lb-count">
            {index + 1} / {images.length}
          </span>
          <button
            className="lb-prev"
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onIndex((index - 1 + images.length) % images.length);
            }}
          >
            ‹
          </button>
          <button
            className="lb-next"
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onIndex((index + 1) % images.length);
            }}
          >
            ›
          </button>
        </>
      )}
      <button className="lb-close" type="button" onClick={onClose} aria-label="Đóng">
        ×
      </button>
    </div>
  );
}
