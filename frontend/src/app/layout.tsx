import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Campvivo",
  description: "Trang thiết bị dã ngoại và du lịch trải nghiệm",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="vi">
      <body>{children}</body>
    </html>
  );
}
