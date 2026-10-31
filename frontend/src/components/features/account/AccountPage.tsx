"use client";

import { useEffect, useState } from "react";
import type { ProductCard } from "@/types/shop";
import { GridCard } from "@/components/features/catalog/ProductCard";
import { useShop } from "@/components/features/shop/ShopProvider";
import { MyOrders } from "@/components/features/account/MyOrders";

type Tab = "orders" | "wishlist" | "address";
const TABS: { id: Tab; label: string }[] = [
  { id: "orders", label: "Đơn hàng của tôi" },
  { id: "wishlist", label: "Danh sách yêu thích" },
  { id: "address", label: "Địa chỉ nhận hàng" },
];

/**
 * Khu vực thành viên: đơn hàng của tôi lấy từ API (FR-12); yêu thích và địa chỉ giao gần nhất còn lưu ở trình duyệt
 * (chưa có API favorites — Tier 2).
 */
export function AccountPage() {
  const { ready, user, wishlist, customer, openLogin, logout } = useShop();
  const [tab, setTab] = useState<Tab>("orders");
  const [wish, setWish] = useState<ProductCard[]>([]);

  useEffect(() => {
    if (tab !== "wishlist" || !wishlist.length) return;
    fetch(`/api/catalog/cards?slugs=${wishlist.join(",")}`)
      .then((r) => r.json())
      .then(setWish)
      .catch(() => {});
  }, [tab, wishlist]);

  if (!ready) return <div className="pageContent" style={{ minHeight: 400 }} />;
  if (!user) {
    return (
      <div className="pageContent cv-success">
        <h1>Tài khoản Campvivo</h1>
        <p>Đăng nhập để xem đơn hàng và danh sách yêu thích.</p>
        <div className="btns">
          <button type="button" className="cv-btn" onClick={openLogin}>
            Đăng nhập
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="pageContent">
      <div className="cv-account">
        <aside>
          <div className="name">{user.name}</div>
          <div>{user.phone}</div>
          {TABS.map((t) => (
            <button key={t.id} type="button" className={tab === t.id ? "active" : ""} onClick={() => setTab(t.id)}>
              {t.label}
            </button>
          ))}
          <button type="button" onClick={logout}>
            Đăng xuất
          </button>
        </aside>
        <section>
          <h1>{TABS.find((t) => t.id === tab)!.label}</h1>
          {tab === "orders" && <MyOrders />}
          {tab === "wishlist" &&
            (wishlist.length ? (
              <div id="ProductCategory" style={{ width: "100%" }}>
                <div className="group_items">
                  {wish.map((p) => (
                    <GridCard key={p.slug} p={p} />
                  ))}
                </div>
              </div>
            ) : (
              <div className="cv-empty">Chưa có sản phẩm yêu thích.</div>
            ))}
          {tab === "address" &&
            (customer ? (
              <div className="cv-order">
                <div className="row" style={{ display: "block" }}>
                  <b>
                    {customer.gender} {customer.name}
                  </b>{" "}
                  – {customer.phone}
                  <br />
                  {[customer.street, customer.ward, customer.district, customer.province].filter(Boolean).join(", ")}
                </div>
              </div>
            ) : (
              <div className="cv-empty">Chưa có địa chỉ. Địa chỉ sẽ được lưu khi bạn đặt hàng.</div>
            ))}
        </section>
      </div>
    </div>
  );
}
