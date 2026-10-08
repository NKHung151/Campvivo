"use client";

import { useEffect } from "react";
import { useShop } from "@/components/features/shop/ShopProvider";
import { WIDGET_HTML } from "@/components/features/layout/widget-html";

/** Floating call/Zalo buttons + the small confirmation toast used by mocked actions. */
export function FloatingChrome() {
  const { toast } = useShop();

  // Dead links (dead-link.ts), including ones inside article/product HTML: do nothing on click.
  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if ((e.target as Element | null)?.closest?.("a[data-dead]")) e.preventDefault();
    };
    document.addEventListener("click", onClick, true);
    return () => document.removeEventListener("click", onClick, true);
  }, []);

  return (
    <>
      <div className="cv-widget" dangerouslySetInnerHTML={{ __html: WIDGET_HTML }} />
      {toast && (
        <div className="cv-toast" role="status">
          {toast}
        </div>
      )}
    </>
  );
}
