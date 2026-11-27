"use client";

import { useState } from "react";
import { useShop } from "@/components/features/shop/ShopProvider";
import { FavoriteList } from "@/components/features/favorite/FavoriteList";
import { MyOrders } from "@/components/features/account/MyOrders";
import { ProfileForm } from "@/components/features/account/ProfileForm";
import { ChangePasswordForm } from "@/components/features/account/ChangePasswordForm";
import { SupportChat } from "@/components/features/support/SupportChat";
import { NotificationList } from "@/components/features/notification/NotificationList";

export type AccountTab = "orders" | "notifications" | "wishlist" | "address" | "profile" | "password" | "support";
type Tab = AccountTab;
const TABS: { id: Tab; label: string }[] = [
  { id: "orders", label: "Đơn hàng của tôi" },
  { id: "notifications", label: "Thông báo" },
  { id: "wishlist", label: "Danh sách yêu thích" },
  { id: "address", label: "Địa chỉ nhận hàng" },
  { id: "support", label: "Hỗ trợ" },
  { id: "profile", label: "Hồ sơ" },
  { id: "password", label: "Đổi mật khẩu" },
];

/**
 * Khu vực thành viên: đơn hàng của tôi lấy từ API (FR-12); hồ sơ và đổi mật khẩu gọi API thật (FR-04); yêu thích từ API
 * (FR-20); địa chỉ giao gần nhất còn lưu ở trình duyệt.
 */
export function AccountPage({ initialTab = "orders" }: { initialTab?: AccountTab }) {
  const { ready, user, canBuy, customer, openLogin, logout } = useShop();
  const [tab, setTab] = useState<Tab>(initialTab);

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
          {tab === "notifications" && <NotificationList />}
          {tab === "support" && <SupportChat />}
          {tab === "profile" && <ProfileForm />}
          {tab === "password" && <ChangePasswordForm />}
          {tab === "wishlist" &&
            (canBuy ? <FavoriteList /> : <div className="cv-empty">Tài khoản nhân viên không có danh sách yêu thích.</div>)}
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
