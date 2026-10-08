"use client";
/* eslint-disable @next/next/no-img-element */

import { useState } from "react";
import { Lightbox } from "@/components/ui/Lightbox";

export function ProductGallery({ images, name }: { images: { big: string; thumb: string; alt: string }[]; name: string }) {
  const [open, setOpen] = useState<number | null>(null);
  // Mock data only has the large thumbnail; the lightbox reuses it.
  const lb = images.map((i) => ({ src: i.thumb, alt: i.alt || name }));
  return (
    <div id="ListImagePreview">
      {images.map((img, i) => (
        <a
          key={img.thumb + i}
          className="item khungAnhCrop"
          href={img.thumb}
          onClick={(e) => {
            e.preventDefault();
            setOpen(i);
          }}
        >
          <img loading={i === 0 ? "eager" : "lazy"} alt={img.alt || name} width={i === 0 ? 1000 : 500} src={img.thumb} />
        </a>
      ))}
      {open !== null && <Lightbox images={lb} index={open} onIndex={setOpen} onClose={() => setOpen(null)} />}
    </div>
  );
}
