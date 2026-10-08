"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import type { CartItem, Customer, Order, User } from "@/types/shop";

/*
 * Client-side mock of campvivo.vn's account/cart backend.
 * Everything persists to localStorage; nothing is sent anywhere.
 * Swap these functions for real API calls when a backend exists.
 */

interface ShopState {
  ready: boolean;
  cart: CartItem[];
  user: User | null;
  orders: Order[];
  customer: Customer | null;
  wishlist: string[];
  viewed: string[];
  loginOpen: boolean;
  toast: string | null;
  addToCart: (item: Omit<CartItem, "key" | "qty">, qty?: number) => void;
  setQty: (key: string, qty: number) => void;
  setVariant: (key: string, variant: string) => void;
  removeFromCart: (key: string) => void;
  clearCart: (keys?: string[]) => void;
  login: (phone: string, name?: string) => void;
  logout: () => void;
  openLogin: () => void;
  closeLogin: () => void;
  saveCustomer: (c: Customer) => void;
  placeOrder: (o: Omit<Order, "id" | "date" | "status">) => Order;
  toggleWishlist: (slug: string) => void;
  markViewed: (slug: string) => void;
  notify: (msg: string) => void;
}

const ShopContext = createContext<ShopState | null>(null);
/** Demo redemption rate for WePoint (placeholder until the promotion module defines it). */
export const POINT_VALUE = 100;
const KEY = "campvivo-shop-v1";

interface Persisted {
  cart: CartItem[];
  user: User | null;
  orders: Order[];
  customer: Customer | null;
  wishlist: string[];
  viewed: string[];
}

const EMPTY: Persisted = { cart: [], user: null, orders: [], customer: null, wishlist: [], viewed: [] };

function load(): Persisted {
  try {
    const raw = window.localStorage.getItem(KEY);
    return raw ? { ...EMPTY, ...(JSON.parse(raw) as Partial<Persisted>) } : EMPTY;
  } catch {
    return EMPTY;
  }
}

export function ShopProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<Persisted>(EMPTY);
  const [ready, setReady] = useState(false);
  const [loginOpen, setLoginOpen] = useState(false);
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
    setReady(true);
  }, []);

  useEffect(() => {
    if (!ready) return;
    try {
      window.localStorage.setItem(KEY, JSON.stringify(state));
    } catch {
      /* storage unavailable: keep in-memory state */
    }
  }, [state, ready]);

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
      loginOpen,
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
      login: (phone, name) =>
        update((s) => ({
          ...s,
          user: { phone, name: name || s.customer?.name || `Camper ${phone.slice(-4)}`, points: s.user?.points ?? 0 },
        })),
      logout: () => update((s) => ({ ...s, user: null })),
      openLogin: () => setLoginOpen(true),
      closeLogin: () => setLoginOpen(false),
      saveCustomer: (c) => update((s) => ({ ...s, customer: c })),
      placeOrder: (o) => {
        const order: Order = {
          ...o,
          id: `WT${Date.now().toString().slice(-8)}`,
          date: new Date().toISOString(),
          status: "Chờ xác nhận",
        };
        update((s) => ({
          ...s,
          orders: [order, ...s.orders],
          // Earn 1 WePoint per 1.000đ; redeemed points are worth POINT_VALUE đ each.
          user: s.user
            ? { ...s.user, points: s.user.points - Math.round(order.points / POINT_VALUE) + Math.floor(order.total / 1000) }
            : s.user,
        }));
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
    [ready, state, loginOpen, toast, update, notify],
  );

  return <ShopContext.Provider value={value}>{children}</ShopContext.Provider>;
}

export function useShop(): ShopState {
  const ctx = useContext(ShopContext);
  if (!ctx) throw new Error("useShop must be used inside <ShopProvider>");
  return ctx;
}
