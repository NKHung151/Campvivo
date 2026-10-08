"use client";

import { useEffect, useState } from "react";
import type { ProductCard } from "@/types/shop";
import { GridCard } from "@/components/features/catalog/ProductCard";
import { useShop } from "@/components/features/shop/ShopProvider";
import { OrderCard } from "@/components/features/cart/OrderCard";

type Tab = "orders" | "wishlist" | "address" | "points";
const TABS: { id: Tab; label: string }[] = [
  { id: "orders", label: "Đơn hàng của tôi" },
  { id: "wishlist", label: "Danh sách yêu thích" },
  { id: "address", label: "Địa chỉ nhận hàng" },
  { id: "points", label: "WePoint" },
];

/**
 * Member area, backed by the mock store until the account API exists, so this is authored
 * from what the site promises members (orders, wishlist, WePoint) using its visual language.
 */
export function AccountPage() {
  const { ready, user, orders, wishlist, customer, openLogin, logout } = useShop();
  const [tab, setTab] = useState<Tab>("orders");
  const [wish, setWish] = useState<ProductCard[]>([]);

  useEffect(() => {
    if (tab !== "wishlist" || !wishlist.length) return;
    fetch(`/api/mock/cards?slugs=${wishlist.join(",")}`)
      .then((r) => r.json())
      .then(setWish)
      .catch(() => {});
  }, [tab, wishlist]);

  if (!ready) return <div className="pageContent" style={{ minHeight: 400 }} />;
  if (!user) {
    return (
      <div className="pageContent cv-success">
        <h1>Tài khoản Campvivo</h1>
        <p>Đăng nhập để xem đơn hàng, danh sách yêu thích và điểm WePoint.</p>
        <div className="btns">
          <button type="button" className="cv-btn" onClick={openLogin}>
            Đăng nhập
          </button>
        </div>
        {orders.length > 0 && (
          <div className="box">
            <p>Đơn hàng đã đặt trên trình duyệt này (khách):</p>
            {orders.map((o) => (
              <OrderCard key={o.id} order={o} />
            ))}
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="pageContent">
      <div className="cv-account">
        <aside>
          <div className="name">{user.name}</div>
          <div>{user.phone}</div>
          <div className="pts">
            WePoint: <b>{user.points.toLocaleString("de-DE")}</b> điểm
          </div>
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
          {tab === "orders" &&
            (orders.length ? orders.map((o) => <OrderCard key={o.id} order={o} />) : <div className="cv-empty">Bạn chưa có đơn hàng nào.</div>)}
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
                  – {customer.phone} – {customer.email}
                  <br />
                  {[customer.street, customer.ward, customer.district, customer.province].filter(Boolean).join(", ")}
                </div>
              </div>
            ) : (
              <div className="cv-empty">Chưa có địa chỉ. Địa chỉ sẽ được lưu khi bạn đặt hàng.</div>
            ))}
          {tab === "points" && (
            <div className="cv-order">
              <div className="row" style={{ display: "block", lineHeight: 1.7 }}>
                Bạn đang có <b>{user.points.toLocaleString("de-DE")}</b> điểm WePoint.
                <br />
                Tích điểm WePoint cho mọi đơn hàng: <b>1.000 VNĐ = 1 điểm</b>. Tri ân <b>5%</b> giá trị đơn hàng cho mọi đơn hàng tiếp theo.
              </div>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
