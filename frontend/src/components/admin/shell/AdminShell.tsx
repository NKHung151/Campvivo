"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { Avatar, Button, Dropdown, Layout, Menu, Result, Space, Spin, Tag, Typography } from "antd";
import type { MenuProps } from "antd";
import {
  AppstoreOutlined,
  CompassOutlined,
  CustomerServiceOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  GiftOutlined,
  LeftOutlined,
  LogoutOutlined,
  ReadOutlined,
  RightOutlined,
  ShopOutlined,
  ShoppingCartOutlined,
  TagsOutlined,
  TeamOutlined,
  UserOutlined,
} from "@ant-design/icons";
import type { Role } from "@/types/api/common";
import { ADMIN_NAV, findNavItem, findParentKey, isNavParent, type AdminNavItem } from "@/lib/admin/nav";
import { useAdmin } from "@/components/admin/shell/AdminSession";
import { loginHref } from "@/lib/auth/redirect";

const { Sider, Header, Content } = Layout;

/**
 * Icon cho mục cấp 1 (theo `key` của mục cha hoặc `href` của mục không có con). Mục con không có icon.
 * Giữ ở đây để lib/admin/nav.ts chỉ là dữ liệu thuần.
 */
const NAV_ICON: Record<string, React.ReactNode> = {
  "/admin": <DashboardOutlined />,
  orders: <ShoppingCartOutlined />,
  "/admin/support": <CustomerServiceOutlined />,
  products: <AppstoreOutlined />,
  combos: <GiftOutlined />,
  "/admin/inventory": <DatabaseOutlined />,
  promotions: <TagsOutlined />,
  articles: <ReadOutlined />,
  "/admin/users": <TeamOutlined />,
};

type MenuItem = Required<MenuProps>["items"][number];

const leaf = (i: AdminNavItem, icon?: React.ReactNode): MenuItem => ({
  key: i.href,
  icon,
  label: <Link href={i.href}>{i.label}</Link>,
});

const ROLE_LABEL: Record<Role, string> = { ADMIN: "Quản trị viên", EMPLOYEE: "Nhân viên", CUSTOMER: "Khách hàng" };

export function AdminShell({ children }: { children: React.ReactNode }) {
  return <ShellFrame>{children}</ShellFrame>;
}

function ShellFrame({ children }: { children: React.ReactNode }) {
  const { ready, status, user, logout, retry } = useAdmin();
  const pathname = usePathname();
  const [collapsed, setCollapsed] = useState(false);

  // Chưa đăng nhập → /login?redirect=<trang hiện tại>. /login thuộc root layout storefront nên tải lại trang.
  // (Lỗi mạng khi khôi phục phiên là status "error", không chuyển hướng: token vẫn còn, cho thử lại.)
  useEffect(() => {
    if (status === "unauthenticated") {
      window.location.replace(loginHref(`${window.location.pathname}${window.location.search}`));
    }
  }, [status]);

  const handleLogout = async () => {
    await logout();
    window.location.assign("/");
  };

  const current = findNavItem(pathname);
  const parentKey = findParentKey(pathname);

  // Mục cha đang mở. Khi chuyển trang, tự mở mục cha chứa trang đó (các mục đang mở khác giữ nguyên).
  const [openKeys, setOpenKeys] = useState<string[]>(parentKey ? [parentKey] : []);
  useEffect(() => {
    if (parentKey) setOpenKeys((keys) => (keys.includes(parentKey) ? keys : [...keys, parentKey]));
  }, [parentKey]);

  const items = useMemo<MenuProps["items"]>(() => {
    if (!user) return [];
    const allowed = (i: AdminNavItem) => (i.roles as string[]).includes(user.role);
    return ADMIN_NAV.map((g) => ({
      type: "group" as const,
      label: g.label,
      children: g.entries.flatMap((e): MenuItem[] => {
        if (!isNavParent(e)) return allowed(e) ? [leaf(e, NAV_ICON[e.href])] : [];
        const kids = e.children.filter(allowed);
        return kids.length ? [{ key: e.key, icon: NAV_ICON[e.key], label: e.label, children: kids.map((k) => leaf(k)) }] : [];
      }),
    })).filter((g) => g.children.length > 0);
  }, [user]);

  if (status === "error") {
    return (
      <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", background: "#f5f5f5" }}>
        <Result
          status="warning"
          title="Không kết nối được máy chủ"
          subTitle="Chưa kiểm tra được phiên đăng nhập của bạn. Vui lòng kiểm tra mạng rồi thử lại."
          extra={
            <Button type="primary" onClick={retry}>
              Thử lại
            </Button>
          }
        />
      </div>
    );
  }

  // Đang khôi phục phiên, hoặc chưa đăng nhập và đang chuyển tới /login.
  if (!ready || !user) {
    return (
      <div style={{ minHeight: "100vh", display: "grid", placeItems: "center" }}>
        <Spin size="large" />
      </div>
    );
  }

  // Khách hàng không vào khu vực admin.
  if (user.role === "CUSTOMER") {
    return (
      <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", background: "#f5f5f5" }}>
        <Result
          status="403"
          title="Không có quyền truy cập"
          subTitle="Khu vực quản trị chỉ dành cho nhân viên và quản trị viên."
          extra={
            <Space direction="vertical">
              {/* <a> thay vì <Link>: rời root layout admin → tải lại trang để nạp CSS storefront */}
              <Button type="primary" href="/">
                Về trang chủ
              </Button>
              <Button onClick={handleLogout}>Đăng xuất</Button>
            </Space>
          }
        />
      </div>
    );
  }

  const userMenu: MenuProps = {
    items: [
      { key: "store", icon: <ShopOutlined />, label: <a href="/">Xem cửa hàng</a> },
      // Đăng xuất mọi thiết bị (POST /auth/logout), xoá token rồi về trang chủ cửa hàng.
      { key: "logout", icon: <LogoutOutlined />, label: "Đăng xuất", onClick: handleLogout },
    ],
  };

  return (
    <Layout style={{ minHeight: "100vh" }}>
      <Sider
        className="cv-admin-sider"
        theme="light"
        collapsible
        trigger={null}
        collapsed={collapsed}
        onCollapse={setCollapsed}
        width={240}
        breakpoint="lg"
        style={{ position: "sticky", top: 0, height: "100vh" }}
      >
        <div className="cv-admin-brand">
          <CompassOutlined className="cv-admin-brand-icon" />
          {!collapsed && (
            <span>
              Campvivo <b>Admin</b>
            </span>
          )}
        </div>
        {/* Chỉ vùng menu cuộn; logo đứng yên. */}
        <div className="cv-admin-menu-scroll">
          <Menu
            theme="light"
            mode="inline"
            selectedKeys={current ? [current.href] : []}
            items={items}
            // Thu gọn: antd tự hiện menu con dạng bay ra khi rê chuột, không điều khiển openKeys
            {...(collapsed ? {} : { openKeys, onOpenChange: setOpenKeys })}
          />
        </div>
        {/* Nút tròn nhỏ nằm trên mép phải sidebar, gần đáy */}
        <button
          type="button"
          className="cv-admin-collapse"
          aria-label={collapsed ? "Mở rộng menu" : "Thu gọn menu"}
          title={collapsed ? "Mở rộng menu" : "Thu gọn menu"}
          onClick={() => setCollapsed((c) => !c)}
        >
          {collapsed ? <RightOutlined /> : <LeftOutlined />}
        </button>
      </Sider>
      <Layout>
        <Header
          style={{
            padding: "0 24px",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            borderBottom: "1px solid #eceef1",
            height: 56,
            lineHeight: "56px",
          }}
        >
          <Typography.Text type="secondary">{current?.label ?? "Quản trị"}</Typography.Text>
          <Space size={16}>
            <Dropdown menu={userMenu} placement="bottomRight">
              <Space style={{ cursor: "pointer" }}>
                <Avatar size="small" icon={<UserOutlined />} />
                <span>{user.fullName}</span>
                <Tag>{ROLE_LABEL[user.role]}</Tag>
              </Space>
            </Dropdown>
          </Space>
        </Header>
        <Content className="cv-admin-content">{children}</Content>
      </Layout>
    </Layout>
  );
}
