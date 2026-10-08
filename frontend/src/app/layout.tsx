import type { Metadata, Viewport } from "next";

export const metadata: Metadata = {
  title: "Campvivo – Trang thiết bị dã ngoại",
  description: "Trang thiết bị dã ngoại và du lịch trải nghiệm",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="vi">
      <body>{children}</body>
    </html>
  );
}
