import Link from "next/link";
import type { AnchorHTMLAttributes } from "react";
import { DEAD } from "@/lib/dead-link";

/**
 * Internal routes go through next/link; anything else (pages not built yet) is a dead link for now — see dead-link.ts.
 */
export function SmartLink({ href, children, ...rest }: AnchorHTMLAttributes<HTMLAnchorElement> & { href: string }) {
  if (href && href.startsWith("/") && !href.startsWith("//")) {
    return (
      <Link href={href} prefetch={false} {...rest}>
        {children}
      </Link>
    );
  }
  // Dead links never open a new tab.
  return (
    <a href={DEAD} data-dead="" {...rest} target={undefined} rel={undefined}>
      {children}
    </a>
  );
}
