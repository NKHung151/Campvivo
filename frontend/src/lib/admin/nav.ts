import type { Role } from "@/types/api/common";

export type StaffRole = Extract<Role, "EMPLOYEE" | "ADMIN">;

export const STAFF: StaffRole[] = ["EMPLOYEE", "ADMIN"];
export const ADMIN_ONLY: StaffRole[] = ["ADMIN"];

/** Một trang admin (mục bấm được). */
export interface AdminNavItem {
  href: string;
  label: string;
  roles: StaffRole[];
}

/** Mục cha mở ra được, chứa các trang con. Hiện khi người dùng xem được ít nhất một trang con. */
export interface AdminNavParent {
  key: string;
  label: string;
  children: AdminNavItem[];
}

export type AdminNavEntry = AdminNavItem | AdminNavParent;

export interface AdminNavGroup {
  label: string;
  entries: AdminNavEntry[];
}

export const isNavParent = (e: AdminNavEntry): e is AdminNavParent => "children" in e;

/** Menu admin — route và quyền theo docs/03-san-pham/pages.md (mục Admin). */
export const ADMIN_NAV: AdminNavGroup[] = [
  { label: "Tổng quan", entries: [{ href: "/admin", label: "Dashboard", roles: ADMIN_ONLY }] },
  {
    label: "Bán hàng",
    entries: [
      {
        key: "orders",
        label: "Đơn hàng",
        children: [
          { href: "/admin/orders", label: "Danh sách đơn hàng", roles: STAFF },
          { href: "/admin/returns", label: "Yêu cầu trả hàng", roles: STAFF },
        ],
      },
      { href: "/admin/support", label: "Hỗ trợ khách hàng", roles: STAFF },
    ],
  },
  {
    label: "Hàng hoá",
    entries: [
      {
        key: "products",
        label: "Sản phẩm",
        children: [
          { href: "/admin/products", label: "Danh sách sản phẩm", roles: ADMIN_ONLY },
          { href: "/admin/products/new", label: "Thêm sản phẩm", roles: ADMIN_ONLY },
          { href: "/admin/categories", label: "Danh mục", roles: ADMIN_ONLY },
        ],
      },
      {
        key: "combos",
        label: "Combo",
        children: [
          { href: "/admin/combos", label: "Danh sách combo", roles: ADMIN_ONLY },
          { href: "/admin/combos/new", label: "Thêm combo", roles: ADMIN_ONLY },
        ],
      },
      { href: "/admin/inventory", label: "Tồn kho", roles: STAFF },
    ],
  },
  {
    label: "Khuyến mãi",
    entries: [
      {
        key: "promotions",
        label: "Khuyến mãi",
        children: [
          { href: "/admin/vouchers", label: "Voucher", roles: ADMIN_ONLY },
          { href: "/admin/campaigns", label: "Chiến dịch giảm giá", roles: ADMIN_ONLY },
          { href: "/admin/campaigns/new", label: "Tạo chiến dịch", roles: ADMIN_ONLY },
        ],
      },
    ],
  },
  {
    label: "Nội dung",
    entries: [
      {
        key: "articles",
        label: "Bài viết",
        children: [
          { href: "/admin/articles", label: "Danh sách bài viết", roles: ADMIN_ONLY },
          { href: "/admin/articles/new", label: "Viết bài mới", roles: ADMIN_ONLY },
        ],
      },
    ],
  },
  { label: "Hệ thống", entries: [{ href: "/admin/users", label: "Người dùng", roles: ADMIN_ONLY }] },
];

const ALL_ITEMS: AdminNavItem[] = ADMIN_NAV.flatMap((g) => g.entries.flatMap((e) => (isNavParent(e) ? e.children : [e])));

/** Trang ứng với một đường dẫn (khớp tiền tố dài nhất; `/admin` chỉ khớp chính nó). */
export function findNavItem(pathname: string): AdminNavItem | null {
  const path = pathname.replace(/\/+$/, "") || "/";
  let best: AdminNavItem | null = null;
  for (const item of ALL_ITEMS) {
    const hit = item.href === "/admin" ? path === "/admin" : path === item.href || path.startsWith(`${item.href}/`);
    if (hit && (!best || item.href.length > best.href.length)) best = item;
  }
  return best;
}

/** Key mục cha chứa trang đang xem (để mở sẵn trong menu), hoặc null. */
export function findParentKey(pathname: string): string | null {
  const item = findNavItem(pathname);
  if (!item) return null;
  for (const g of ADMIN_NAV) {
    for (const e of g.entries) {
      if (isNavParent(e) && e.children.some((c) => c.href === item.href)) return e.key;
    }
  }
  return null;
}

/** Trang mặc định sau khi vào admin theo role. */
export function homeFor(role: StaffRole): string {
  return role === "ADMIN" ? "/admin" : "/admin/orders";
}
