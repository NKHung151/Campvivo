"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import type { Customer, User } from "@/types/shop";
import type { Cart } from "@/types/api/order";
import { useAuth } from "@/components/features/auth/AuthProvider";
import { loginHref } from "@/lib/auth/redirect";
import { getErrorMessage } from "@/lib/errorMessages";
import * as cartApi from "@/services/cartService";

/*
 * Trạng thái dùng chung của storefront.
 * - Đăng nhập: phiên thật (AuthProvider). Giỏ hàng: API /cart (FR-09) — chỉ khách hàng (role CUSTOMER) có giỏ.
 * - Đơn hàng: API /orders (FR-12) — không lưu ở đây.
 * - Còn lưu localStorage (chưa có API): thông tin giao hàng gần nhất (`customer`, kèm mã GHN), yêu thích, đã xem.
 */

export type CartStatus = "idle" | "loading" | "ready" | "error";

/** Mặt hàng thêm vào giỏ: đúng một trong hai id. */
export type CartItemRef = { productId: number; comboId?: never } | { comboId: number; productId?: never };

interface ShopState {
  ready: boolean;
  user: User | null;
  /** Đăng nhập bằng tài khoản khách hàng (nhân viên/admin không mua hàng). */
  canBuy: boolean;
  cart: Cart | null;
  cartStatus: CartStatus;
  cartCount: number;
  customer: Customer | null;
  wishlist: string[];
  viewed: string[];
  toast: string | null;
  /** Thêm vào giỏ (chưa đăng nhập → chuyển /login). Trả về true nếu thành công; lỗi đã được báo bằng toast. */
  addToCart: (item: CartItemRef, qty?: number) => Promise<boolean>;
  updateCartItem: (cartItemId: number, qty: number) => Promise<void>;
  removeCartItem: (cartItemId: number) => Promise<void>;
  refreshCart: () => Promise<void>;
  /** Đăng xuất (API + xoá token). */
  logout: () => void;
  /** Chuyển tới /login, quay lại trang hiện tại sau khi đăng nhập. */
  openLogin: () => void;
  saveCustomer: (c: Customer) => void;
  toggleWishlist: (slug: string) => void;
  markViewed: (slug: string) => void;
  notify: (msg: string) => void;
}

const ShopContext = createContext<ShopState | null>(null);
const KEY = "campvivo-shop-v1";

interface Persisted {
  customer: Customer | null;
  wishlist: string[];
  viewed: string[];
}

const EMPTY: Persisted = { customer: null, wishlist: [], viewed: [] };

function load(): Persisted {
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) return EMPTY;
    // Bỏ dữ liệu mock cũ: `user` (đăng nhập giả), `cart`, `orders` (giỏ và đơn giờ nằm ở server).
    const { user: _legacyUser, cart: _legacyCart, orders: _legacyOrders, ...saved } = JSON.parse(raw) as Partial<Persisted> & {
      user?: unknown;
      cart?: unknown;
      orders?: unknown;
    };
    return { ...EMPTY, ...saved };
  } catch {
    return EMPTY;
  }
}

export function ShopProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<Persisted>(EMPTY);
  const [storeReady, setStoreReady] = useState(false);
  const [cart, setCart] = useState<Cart | null>(null);
  const [cartStatus, setCartStatus] = useState<CartStatus>("idle");
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

  const authUser = auth.user;
  const canBuy = authUser?.role === "CUSTOMER";
  // Sẵn sàng khi đã nạp localStorage VÀ biết đã đăng nhập hay chưa (phiên thật).
  const ready = storeReady && auth.status !== "loading";
  const user = useMemo<User | null>(
    () => (authUser ? { name: authUser.fullName, phone: authUser.phone ?? "" } : null),
    [authUser],
  );
  const { logout: authLogout } = auth;

  const notify = useCallback((msg: string) => {
    setToast(msg);
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(null), 2600);
  }, []);

  const refreshCart = useCallback(async () => {
    setCartStatus((s) => (s === "ready" ? s : "loading"));
    try {
      setCart(await cartApi.getCart());
      setCartStatus("ready");
    } catch {
      setCartStatus("error");
    }
  }, []);

  // Giỏ theo phiên: khách hàng đăng nhập → tải giỏ; đăng xuất / tài khoản nhân viên → không có giỏ.
  useEffect(() => {
    if (auth.status === "loading") return;
    if (canBuy) {
      void refreshCart();
    } else {
      setCart(null);
      setCartStatus("idle");
    }
  }, [auth.status, canBuy, authUser?.id, refreshCart]);

  const update = useCallback((fn: (s: Persisted) => Persisted) => {
    if (pending.current) pending.current.push(fn);
    else setState((s) => fn(s));
  }, []);

  const openLogin = useCallback(
    // Đọc URL lúc bấm (không dùng useSearchParams để tránh bắt buộc Suspense ở mọi trang storefront).
    () => router.push(loginHref(`${window.location.pathname}${window.location.search}`)),
    [router],
  );

  const addToCart = useCallback(
    async (item: CartItemRef, qty = 1) => {
      if (!authUser) {
        notify("Vui lòng đăng nhập để thêm vào giỏ hàng");
        openLogin();
        return false;
      }
      if (!canBuy) {
        notify("Tài khoản nhân viên không dùng để mua hàng");
        return false;
      }
      try {
        setCart(await cartApi.addCartItem({ ...item, quantity: qty }));
        setCartStatus("ready");
        return true;
      } catch (err) {
        notify(getErrorMessage(err));
        return false;
      }
    },
    [authUser, canBuy, notify, openLogin],
  );

  const updateCartItem = useCallback(
    async (cartItemId: number, qty: number) => {
      try {
        setCart(await cartApi.updateCartItem(cartItemId, qty));
      } catch (err) {
        notify(getErrorMessage(err));
        await refreshCart();
      }
    },
    [notify, refreshCart],
  );

  const removeCartItem = useCallback(
    async (cartItemId: number) => {
      try {
        setCart(await cartApi.removeCartItem(cartItemId));
      } catch (err) {
        notify(getErrorMessage(err));
        await refreshCart();
      }
    },
    [notify, refreshCart],
  );

  const value = useMemo<ShopState>(
    () => ({
      ready,
      ...state,
      user,
      canBuy,
      cart,
      cartStatus,
      cartCount: cart?.itemCount ?? 0,
      toast,
      addToCart,
      updateCartItem,
      removeCartItem,
      refreshCart,
      logout: () => void authLogout(),
      openLogin,
      saveCustomer: (c) => update((s) => ({ ...s, customer: c })),
      toggleWishlist: (slug) =>
        update((s) => ({
          ...s,
          wishlist: s.wishlist.includes(slug) ? s.wishlist.filter((w) => w !== slug) : [slug, ...s.wishlist],
        })),
      markViewed: (slug) =>
        update((s) => ({ ...s, viewed: [slug, ...s.viewed.filter((v) => v !== slug)].slice(0, 12) })),
      notify,
    }),
    [ready, state, user, canBuy, cart, cartStatus, toast, addToCart, updateCartItem, removeCartItem, refreshCart,
      authLogout, openLogin, update, notify],
  );

  return <ShopContext.Provider value={value}>{children}</ShopContext.Provider>;
}

export function useShop(): ShopState {
  const ctx = useContext(ShopContext);
  if (!ctx) throw new Error("useShop must be used inside <ShopProvider>");
  return ctx;
}
