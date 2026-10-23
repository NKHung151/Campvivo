import type { Metadata, Viewport } from "next";
import "@/styles/shop/base.css";
import "@/styles/shop/home.css";
import "@/styles/shop/shop.css";
import "@/styles/shop/brand.css";
import "@/styles/shop/news.css";
import "@/styles/shop/info.css";
import "@/styles/shop/guides.css";
import "@/styles/shop/search.css";
import "@/styles/shop/journal.css";
import "@/styles/shop/custom.css";
import "@/styles/shop/ui.css";
import "@/styles/shop/auth.css";
import { AuthProvider } from "@/components/features/auth/AuthProvider";

/**
 * Root layout của storefront (route group `(store)`: cửa hàng, tài khoản, đăng nhập). CSS storefront nạp
 * ở đây để mọi trang — kể cả trang 404 — đều có; khu vực admin dùng root layout riêng `(admin)`.
 * AuthProvider (phiên đăng nhập thật) bọc toàn bộ; token dùng chung với root layout admin qua localStorage.
 */
export const metadata: Metadata = {
  title: "Campvivo – Trang thiết bị dã ngoại",
  description: "Trang thiết bị dã ngoại và du lịch trải nghiệm",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
};

export default function StoreRootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="vi">
      <body>
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
