"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import type { Customer, User } from "@/types/shop";
import type { Cart } from "@/types/api/order";
import { useAuth } from "@/components/features/auth/AuthProvider";
import { loginHref } from "@/lib/auth/redirect";
import { getErrorMessage } from "@/lib/errorMessages";
import { isApiError } from "@/services/apiError";
import * as cartApi from "@/services/cartService";
import * as favoriteApi from "@/services/favoriteService";
import type { FavoriteRef, FavoriteType } from "@/types/api/favorite";

/*
 * Trạng thái dùng chung của storefront.
 * - Đăng nhập: phiên thật (AuthProvider). Giỏ hàng: API /cart (FR-09) — chỉ khách hàng (role CUSTOMER) có giỏ.
 * - Yêu thích: id các mục đã thích từ API /users/me/favorites/ids (FR-20, D-47) — chỉ khách hàng.
 * - Đơn hàng: API /orders (FR-12) — không lưu ở đây.
 * - Còn lưu localStorage (chưa có API): thông tin giao hàng gần nhất (`customer`, kèm mã GHN), đã xem.
 */

export type CartStatus = "idle" | "loading" | "ready" | "error";

/** Mặt hàng thêm vào giỏ: đúng một trong hai id; `variantId` = màu / size đã chọn (D-40). */
export type CartItemRef = { productId: number; variantId?: number; comboId?: never } | { comboId: number; productId?: never; variantId?: never };

/** Mục yêu thích: loại + id. */
export interface FavoriteKey {
  type: FavoriteType;
  id: number;
}

const favKey = (k: FavoriteKey) => `${k.type}:${k.id}`;
const favRef = (k: FavoriteKey): FavoriteRef =>
  k.type === "PRODUCT" ? { productId: k.id } : k.type === "COMBO" ? { comboId: k.id } : { articleId: k.id };

interface ShopState {
  ready: boolean;
  user: User | null;
  /** Đăng nhập bằng tài khoản khách hàng (nhân viên/admin không mua hàng). */
  canBuy: boolean;
  cart: Cart | null;
  cartStatus: CartStatus;
  cartCount: number;
  customer: Customer | null;
  viewed: string[];
  toast: string | null;
  /** Đã thích chưa (false khi chưa đăng nhập / chưa tải xong). */
  isFavorite: (item: FavoriteKey) => boolean;
  /**
   * Thêm / bỏ yêu thích (chưa đăng nhập → /login; nhân viên → thông báo). Đổi ngay trên giao diện, lỗi thì trả lại như cũ.
   * Trả về trạng thái sau cùng.
   */
  toggleFavorite: (item: FavoriteKey) => Promise<boolean>;
  /** Thêm (không bỏ nếu đã thích) — nút "Yêu thích" ở giỏ hàng. */
  addFavorite: (item: FavoriteKey) => Promise<void>;
  /** Đồng bộ sau khi trang khác bỏ thích (vd. tab yêu thích của tài khoản). */
  forgetFavorite: (item: FavoriteKey) => void;
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
  markViewed: (slug: string) => void;
  notify: (msg: string) => void;
}

const ShopContext = createContext<ShopState | null>(null);

/** Lỗi giỏ hàng: giữ câu cụ thể của server (chưa chọn màu / size, biến thể nào hết hàng) thay vì câu chung theo mã lỗi. */
function cartErrorMessage(err: unknown): string {
  if (isApiError(err)) {
    if (err.fieldErrors.variantId) return err.fieldErrors.variantId;
    if ((err.errorCode === "OUT_OF_STOCK" || err.errorCode === "PRODUCT_INACTIVE") && err.message) return err.message;
  }
  return getErrorMessage(err);
}
const KEY = "campvivo-shop-v1";

interface Persisted {
  customer: Customer | null;
  viewed: string[];
}

const EMPTY: Persisted = { customer: null, viewed: [] };

function load(): Persisted {
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) return EMPTY;
    // Bỏ dữ liệu mock cũ: `user` (đăng nhập giả), `cart`, `orders`, `wishlist` (giỏ, đơn, yêu thích giờ nằm ở server).
    const {
      user: _legacyUser,
      cart: _legacyCart,
      orders: _legacyOrders,
      wishlist: _legacyWishlist,
      ...saved
    } = JSON.parse(raw) as Partial<Persisted> & {
      user?: unknown;
      cart?: unknown;
      orders?: unknown;
      wishlist?: unknown;
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

  // Yêu thích theo phiên: khách hàng → tải tập id; còn lại → rỗng.
  const [favorites, setFavorites] = useState<ReadonlySet<string>>(() => new Set());
  useEffect(() => {
    if (auth.status === "loading") return;
    setFavorites(new Set());
    if (!canBuy) return;
    let cancelled = false;
    favoriteApi
      .getFavoriteIds()
      .then((ids) => {
        if (cancelled) return;
        setFavorites(
          new Set([
            ...ids.productIds.map((id) => favKey({ type: "PRODUCT", id })),
            ...ids.comboIds.map((id) => favKey({ type: "COMBO", id })),
            ...ids.articleIds.map((id) => favKey({ type: "ARTICLE", id })),
          ]),
        );
      })
      .catch(() => {
        /* không tải được: tim hiện "chưa thích", bấm vẫn gọi API bình thường */
      });
    return () => {
      cancelled = true;
    };
  }, [auth.status, canBuy, authUser?.id]);

  const setFavorite = useCallback((item: FavoriteKey, on: boolean) => {
    setFavorites((prev) => {
      const key = favKey(item);
      if (prev.has(key) === on) return prev;
      const next = new Set(prev);
      if (on) next.add(key);
      else next.delete(key);
      return next;
    });
  }, []);

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
        notify(cartErrorMessage(err));
        return false;
      }
    },
    [authUser, canBuy, notify, openLogin],
  );

  /** Kiểm đăng nhập + role trước khi đụng tới yêu thích; false = đã báo cho người dùng. */
  const canFavorite = useCallback(() => {
    if (!authUser) {
      notify("Vui lòng đăng nhập để lưu yêu thích");
      openLogin();
      return false;
    }
    if (!canBuy) {
      notify("Tài khoản nhân viên không dùng danh sách yêu thích");
      return false;
    }
    return true;
  }, [authUser, canBuy, notify, openLogin]);

  const toggleFavorite = useCallback(
    async (item: FavoriteKey) => {
      const was = favorites.has(favKey(item));
      if (!canFavorite()) return was;
      setFavorite(item, !was);
      try {
        if (was) await favoriteApi.removeFavorite(favRef(item));
        else await favoriteApi.addFavorite(favRef(item));
        notify(was ? "Đã bỏ khỏi danh sách yêu thích" : "Đã thêm vào danh sách yêu thích");
        return !was;
      } catch (err) {
        setFavorite(item, was);
        notify(getErrorMessage(err));
        return was;
      }
    },
    [favorites, canFavorite, setFavorite, notify],
  );

  const addFavorite = useCallback(
    async (item: FavoriteKey) => {
      if (!canFavorite()) return;
      try {
        await favoriteApi.addFavorite(favRef(item));
        setFavorite(item, true);
        notify("Đã thêm vào danh sách yêu thích");
      } catch (err) {
        notify(getErrorMessage(err));
      }
    },
    [canFavorite, setFavorite, notify],
  );

  const updateCartItem = useCallback(
    async (cartItemId: number, qty: number) => {
      try {
        setCart(await cartApi.updateCartItem(cartItemId, qty));
      } catch (err) {
        notify(cartErrorMessage(err));
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
      isFavorite: (item) => favorites.has(favKey(item)),
      toggleFavorite,
      addFavorite,
      forgetFavorite: (item) => setFavorite(item, false),
      markViewed: (slug) =>
        update((s) => ({ ...s, viewed: [slug, ...s.viewed.filter((v) => v !== slug)].slice(0, 12) })),
      notify,
    }),
    [ready, state, user, canBuy, cart, cartStatus, toast, addToCart, updateCartItem, removeCartItem, refreshCart,
      authLogout, openLogin, update, notify, favorites, toggleFavorite, addFavorite, setFavorite],
  );

  return <ShopContext.Provider value={value}>{children}</ShopContext.Provider>;
}

export function useShop(): ShopState {
  const ctx = useContext(ShopContext);
  if (!ctx) throw new Error("useShop must be used inside <ShopProvider>");
  return ctx;
}
