import type { LinkItem } from "@/types/shop";
import { SmartLink } from "@/components/ui/SmartLink";
import { DEAD } from "@/lib/dead-link";

export function ShareIcons() {
  return (
    <div id="CommonShareThuCong">
      <a href={DEAD} data-dead="" rel="nofollow noreferrer" title="Share on Facebook" className="shareIcon icon1">
        &nbsp;
      </a>
      <a href={DEAD} data-dead="" rel="nofollow noreferrer" title="Share on Twitter" className="shareIcon icon2">
        &nbsp;
      </a>
      <a href="#" rel="nofollow" title="Print this page" className="shareIcon icon4">
        &nbsp;
      </a>
      <a href={DEAD} data-dead="" rel="nofollow" title="Email to friend" className="shareIcon icon5">
        &nbsp;
      </a>
    </div>
  );
}

export function Breadcrumb({ items }: { items: LinkItem[] }) {
  return (
    <nav id="Breadcrumb" aria-label="Breadcrumb">
      <ol className="road">
        {items.map((b, i) => (
          <li key={b.href + i}>
            <SmartLink className={`lv1${i ? " arrow" : ""}`} href={b.href} title={b.name}>
              {b.name}
            </SmartLink>
          </li>
        ))}
      </ol>
      <ShareIcons />
    </nav>
  );
}
