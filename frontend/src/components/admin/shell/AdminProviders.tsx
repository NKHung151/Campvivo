"use client";

import { App, ConfigProvider } from "antd";
import viVN from "antd/locale/vi_VN";

/** Theme + locale Ant Design cho khu vực admin (chỉ nạp trong root layout (admin)). */
export function AdminProviders({ children }: { children: React.ReactNode }) {
  return (
    <ConfigProvider
      locale={viVN}
      theme={{
        token: {
          // Xanh rừng — cùng màu logo storefront (styles/shop/custom.css).
          colorPrimary: "#2f6a3f",
          borderRadius: 6,
          fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
        },
        components: {
          Layout: { siderBg: "#ffffff", headerBg: "#ffffff" },
          // Sidebar sáng: mục chọn nền xám nhạt + chữ/icon màu chủ đạo (styles/admin/admin.css làm đậm chữ)
          Menu: {
            itemBg: "#ffffff",
            itemColor: "rgba(0, 0, 0, 0.78)",
            itemHoverBg: "#f5f5f5",
            itemHoverColor: "rgba(0, 0, 0, 0.88)",
            itemSelectedBg: "#f0f2f0",
            itemSelectedColor: "#2f6a3f",
            itemActiveBg: "#eceeec",
            groupTitleColor: "rgba(0, 0, 0, 0.4)",
            groupTitleFontSize: 11,
            itemHeight: 40,
            itemMarginInline: 8,
            itemBorderRadius: 8,
            iconSize: 16,
            iconMarginInlineEnd: 12,
            activeBarBorderWidth: 0,
          },
        },
      }}
    >
      {/* <App> để dùng message/modal/notification qua App.useApp() trong mọi trang */}
      <App>{children}</App>
    </ConfigProvider>
  );
}
