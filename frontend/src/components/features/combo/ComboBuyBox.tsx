"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useShop } from "@/components/features/shop/ShopProvider";

/** Chọn số lượng bộ + Mua ngay / Thêm vào giỏ cho trang combo (FR-09). Giỏ hàng ở server; lỗi do ShopProvider báo. */
export function ComboBuyBox({ comboId, available }: { comboId: number; available: number }) {
  const { addToCart, notify } = useShop();
  const router = useRouter();
  const [qty, setQty] = useState(1);
  const [adding, setAdding] = useState(false);
  const max = Math.max(available, 1);

  const add = async (go: boolean) => {
    if (adding) return;
    setAdding(true);
    const ok = await addToCart({ comboId }, qty);
    setAdding(false);
    if (!ok) return;
    if (go) router.push("/cart");
    else notify("Đã thêm combo vào giỏ hàng");
  };

  if (available <= 0) {
    return (
      <div className="groupButton">
        <button type="button" className="btBuy1" disabled>
          Hết hàng
        </button>
      </div>
    );
  }

  return (
    <>
      <div>
        <div className="propertyname">Số lượng:</div>
        <div className="bgtextbox">
          <div className="pdp-qty-stepper">
            <button type="button" className="qty-btn qty-minus" aria-label="Giảm" onClick={() => setQty((q) => Math.max(1, q - 1))}>
              −
            </button>
            <input
              type="number"
              className="textbox qty-input"
              min={1}
              max={max}
              value={qty}
              onChange={(e) => setQty(Math.max(1, Math.min(max, +e.target.value || 1)))}
            />
            <button type="button" className="qty-btn qty-plus" aria-label="Tăng" onClick={() => setQty((q) => Math.min(max, q + 1))}>
              +
            </button>
          </div>
        </div>
      </div>
      <div className="groupButton">
        <button type="button" className="btBuy1" disabled={adding} onClick={() => void add(true)}>
          Mua ngay
        </button>
        <button type="button" className="btBuy2" disabled={adding} onClick={() => void add(false)}>
          Thêm vào giỏ
        </button>
      </div>
    </>
  );
}
