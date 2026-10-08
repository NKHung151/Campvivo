"use client";

import { useState } from "react";

/**
 * #CommonBrandHeader: a still background image with a muted YouTube clip playing over it
 * (an autoplaying muted YouTube embed acts as the background video).
 */
export function BrandHeader({ img, video, cls }: { img: string | null; video: string | null; cls: string }) {
  const [muted, setMuted] = useState(true);
  return (
    <div
      id="CommonBrandHeader"
      className={cls}
      style={img ? { background: `url(${img}) no-repeat top center`, backgroundSize: "auto 100%" } : undefined}
    >
      <div className="frameHead">
        {video && (
          <iframe
            key={String(muted)}
            title="Campvivo brand video"
            src={`https://www.youtube.com/embed/${video}?autoplay=1&mute=${muted ? 1 : 0}&controls=0&loop=1&playlist=${video}&modestbranding=1&playsinline=1&rel=0`}
            allow="autoplay; encrypted-media"
            style={{ position: "absolute", top: "50%", left: "50%", width: "177.78vh", minWidth: "100%", height: "56.25vw", minHeight: "100%", transform: "translate(-50%,-50%)", border: 0, pointerEvents: "none" }}
          />
        )}
        {video && (
          <div className="wButtonFunctionVideo" style={{ display: "block" }}>
            <div className="ButtonFunctionVideo">
              <a
                href="#"
                className={`muted${muted ? "" : " unmute"}`}
                onClick={(e) => {
                  e.preventDefault();
                  setMuted((m) => !m);
                }}
              >
                <span id="textfunctionvideo" />
              </a>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
