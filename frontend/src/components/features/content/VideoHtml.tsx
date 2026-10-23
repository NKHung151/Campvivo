"use client";

import { useState } from "react";
import { Lightbox } from "@/components/ui/Lightbox";

/** YouTube id from the watch / youtu.be links wrapped in `a[data-fancybox]`. */
function youtubeId(href: string): string | null {
  try {
    const u = new URL(href);
    if (u.hostname === "youtu.be") return u.pathname.slice(1) || null;
    if (/(^|\.)youtube\.com$/.test(u.hostname)) return u.searchParams.get("v");
  } catch {
    /* not a URL */
  }
  return null;
}

/** Rich HTML content whose fancybox video links play in the in-page lightbox instead of leaving the site. */
export function VideoHtml({ html, className }: { html: string; className?: string }) {
  const [video, setVideo] = useState<string | null>(null);
  return (
    <>
      <div
        className={className}
        dangerouslySetInnerHTML={{ __html: html }}
        onClick={(e) => {
          const a = (e.target as HTMLElement).closest("a[data-fancybox]") as HTMLAnchorElement | null;
          const id = a && youtubeId(a.href);
          if (id) {
            e.preventDefault();
            setVideo(id);
          }
        }}
      />
      {video && (
        <Lightbox onClose={() => setVideo(null)}>
          <iframe src={`https://www.youtube.com/embed/${video}?autoplay=1`} allow="autoplay; encrypted-media" allowFullScreen title="Campvivo video" />
        </Lightbox>
      )}
    </>
  );
}
