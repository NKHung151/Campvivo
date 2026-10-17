import { ShopFrame } from "@/components/features/layout/ShopFrame";

export default function ShopLayout({ children }: { children: React.ReactNode }) {
  return <ShopFrame>{children}</ShopFrame>;
}
