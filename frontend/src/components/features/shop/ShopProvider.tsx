"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import type { CartItem, Customer, Order, User } from "@/types/shop";
import { useAuth } from "@/components/features/auth/AuthProvider";
import { loginHref } from "@/lib/auth/redirect";

/*
 * Client-side mock of campvivo.vn's cart/order backend (giỏ hàng, đơn hàng, yêu thích).
 * Everything persists to localStorage; nothing is sent anywhere.
 * Swap these functions for real API calls when a backend exists.
 * Đăng nhập KHÔNG còn là mock: `user` lấy từ phiên thật (AuthProvider, POST /auth/login), `openLogin` chuyển tới /login.
 */

interface ShopState {
  ready: boolean;
  cart: CartItem[];
  user: User | null;
  orders: Order[];
  customer: Customer | null;
  wishlist: string[];
  viewed: string[];
  toast: string | null;
  addToCart: (item: Omit<CartItem, "key" | "qty">, qty?: number) => void;
  setQty: (key: string, qty: number) => void;
  setVariant: (key: string, variant: string) => void;
  removeFromCart: (key: string) => void;
  clearCart: (keys?: string[]) => void;
  /** Đăng xuất (API + xoá token). */
  logout: () => void;
  /** Chuyển tới /login, quay lại trang hiện tại sau khi đăng nhập. */
  openLogin: () => void;
  saveCustomer: (c: Customer) => void;
  placeOrder: (o: Omit<Order, "id" | "date" | "status">) => Order;
  toggleWishlist: (slug: string) => void;
  markViewed: (slug: string) => void;
  notify: (msg: string) => void;
}

const ShopContext = createContext<ShopState | null>(null);
const KEY = "campvivo-shop-v1";

interface Persisted {
  cart: CartItem[];
  orders: Order[];
  customer: Customer | null;
  wishlist: string[];
  viewed: string[];
}

const EMPTY: Persisted = { cart: [], orders: [], customer: null, wishlist: [], viewed: [] };

function load(): Persisted {
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) return EMPTY;
    // Bỏ `user` còn sót từ bản đăng nhập giả cũ: người dùng giờ lấy từ phiên thật.
    const { user: _legacyMockUser, ...saved } = JSON.parse(raw) as Partial<Persisted> & { user?: unknown };
    return { ...EMPTY, ...saved };
  } catch {
    return EMPTY;
  }
}

export function ShopProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<Persisted>(EMPTY);
  const [storeReady, setStoreReady] = useState(false);
  const router = useRouter();
  const auth = useAuth();
  const [toast, setToast] = useState<string | null>(null);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Children's mount effects (e.g. markViewed) run before ours; queue them until storage is loaded.
  const pending = useRef<((s: Persisted) => Persisted)[] | null>([]);

  useEffect(() => {
    // Hydrate from localStorage after mount so server and client markup match.
    const queued = pending.current;
    if (!queued) return; // StrictMode re-run: already loaded
    pending.current = null;
    setState(queued.reduce((s, fn) => fn(s), load()));
    setStoreReady(true);
  }, []);

  useEffect(() => {
    if (!storeReady) return;
    try {
      window.localStorage.setItem(KEY, JSON.stringify(state));
    } catch {
      /* storage unavailable: keep in-memory state */
    }
  }, [state, storeReady]);

  // Sẵn sàng khi đã nạp giỏ hàng (storage) VÀ biết đã đăng nhập hay chưa (phiên thật).
  const ready = storeReady && auth.status !== "loading";
  // Người dùng hiển thị ở storefront: map từ hồ sơ thật. Phiên chưa xác định / chưa đăng nhập → khách.
  const authUser = auth.user;
  const user = useMemo<User | null>(
    () => (authUser ? { name: authUser.fullName, phone: authUser.phone ?? "" } : null),
    [authUser],
  );
  const { logout: authLogout } = auth;

  const update = useCallback((fn: (s: Persisted) => Persisted) => {
    if (pending.current) pending.current.push(fn);
    else setState((s) => fn(s));
  }, []);

  const notify = useCallback((msg: string) => {
    setToast(msg);
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(null), 2600);
  }, []);

  const value = useMemo<ShopState>(
    () => ({
      ready,
      ...state,
      user,
      toast,
      addToCart: (item, qty = 1) =>
        update((s) => {
          const key = `${item.slug}|${item.variant ?? ""}`;
          const existing = s.cart.find((c) => c.key === key);
          const cart = existing
            ? s.cart.map((c) => (c.key === key ? { ...c, qty: c.qty + qty } : c))
            : [...s.cart, { ...item, key, qty }];
          return { ...s, cart };
        }),
      setQty: (key, qty) =>
        update((s) => ({ ...s, cart: s.cart.map((c) => (c.key === key ? { ...c, qty: Math.max(1, qty) } : c)) })),
      setVariant: (key, variant) =>
        update((s) => {
          const target = s.cart.find((c) => c.key === key);
          if (!target) return s;
          const newKey = `${target.slug}|${variant}`;
          const dup = s.cart.find((c) => c.key === newKey && c.key !== key);
          // Switching to a variant already in the cart merges the two lines.
          const cart = dup
            ? s.cart.filter((c) => c.key !== key).map((c) => (c.key === newKey ? { ...c, qty: c.qty + target.qty } : c))
            : s.cart.map((c) => (c.key === key ? { ...c, variant, key: newKey } : c));
          return { ...s, cart };
        }),
      removeFromCart: (key) => update((s) => ({ ...s, cart: s.cart.filter((c) => c.key !== key) })),
      clearCart: (keys) => update((s) => ({ ...s, cart: keys ? s.cart.filter((c) => !keys.includes(c.key)) : [] })),
      logout: () => void authLogout(),
      // Đọc URL lúc bấm (không dùng useSearchParams để tránh bắt buộc Suspense ở mọi trang storefront).
      openLogin: () => router.push(loginHref(`${window.location.pathname}${window.location.search}`)),
      saveCustomer: (c) => update((s) => ({ ...s, customer: c })),
      placeOrder: (o) => {
        const order: Order = {
          ...o,
          id: `WT${Date.now().toString().slice(-8)}`,
          date: new Date().toISOString(),
          status: "Chờ xác nhận",
        };
        update((s) => ({ ...s, orders: [order, ...s.orders] }));
        return order;
      },
      toggleWishlist: (slug) =>
        update((s) => ({
          ...s,
          wishlist: s.wishlist.includes(slug) ? s.wishlist.filter((w) => w !== slug) : [slug, ...s.wishlist],
        })),
      markViewed: (slug) =>
        update((s) => ({ ...s, viewed: [slug, ...s.viewed.filter((v) => v !== slug)].slice(0, 12) })),
      notify,
    }),
    [ready, state, user, toast, update, notify, authLogout, router],
  );

  return <ShopContext.Provider value={value}>{children}</ShopContext.Provider>;
}

export function useShop(): ShopState {
  const ctx = useContext(ShopContext);
  if (!ctx) throw new Error("useShop must be used inside <ShopProvider>");
  return ctx;
}
