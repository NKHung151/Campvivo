import type { Metadata, Viewport } from "next";
import { AntdRegistry } from "@ant-design/nextjs-registry";
import { AdminProviders } from "@/components/admin/shell/AdminProviders";
import "@/styles/admin/admin.css";

/**
 * Root layout riêng của khu vực admin (route group `(admin)`). Tách khỏi `(store)` để CSS storefront
 * (styles/shop/*) không lẫn vào admin: chuyển giữa hai root layout luôn tải lại toàn trang.
 */
export const metadata: Metadata = {
  title: { default: "Campvivo Admin", template: "%s | Campvivo Admin" },
  robots: { index: false, follow: false },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
};

export default function AdminRootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="vi">
      <body>
        <AntdRegistry>
          <AdminProviders>{children}</AdminProviders>
        </AntdRegistry>
      </body>
    </html>
  );
}
