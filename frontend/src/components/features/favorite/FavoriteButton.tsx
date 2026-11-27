"use client";

import { useState } from "react";
import { useShop, type FavoriteKey } from "@/components/features/shop/ShopProvider";

/**
 * Nút "Thêm vào Danh sách yêu thích" ở trang sản phẩm / combo (FR-20). Trạng thái lấy từ ShopProvider (D-47); chưa đăng nhập →
 * /login, nhân viên → thông báo (ShopProvider lo).
 */
export function FavoriteButton({ item }: { item: FavoriteKey }) {
  const { isFavorite, toggleFavorite } = useShop();
  const [busy, setBusy] = useState(false);
  const fav = isFavorite(item);

  return (
    <a
      className={`addtofav${fav ? " active" : ""}`}
      href="#"
      role="button"
      aria-pressed={fav}
      aria-busy={busy}
      onClick={(e) => {
        e.preventDefault();
        if (busy) return;
        setBusy(true);
        void toggleFavorite(item).finally(() => setBusy(false));
      }}
    >
      {fav ? "Đã thêm vào Danh sách yêu thích" : "Thêm vào Danh sách yêu thích"}
    </a>
  );
}
