"use client";
/* eslint-disable @next/next/no-img-element */

import Link from "next/link";
import { useEffect, useState } from "react";
import type { CartLine } from "@/types/api/order";
import { comboHref, productHref, vnd } from "@/lib/format";

const LOW_STOCK = 5;

/** Vấn đề của một dòng giỏ (BR-1, BR-14) — dòng có vấn đề phải xử lý trước khi đặt hàng. */
export function lineProblem(l: CartLine): string | null {
  if (!l.item.isActive) return "Sản phẩm đã ngừng kinh doanh";
  if (l.item.availableQuantity <= 0) return "Tạm hết hàng";
  if (l.quantity > l.item.availableQuantity) return `Chỉ còn ${l.item.availableQuantity} sản phẩm`;
  return null;
}

const hrefOf = (l: CartLine) => (l.item.type === "COMBO" ? comboHref(l.item.slug) : productHref(l.item.slug));

/** Ô số lượng: sửa tại chỗ, chỉ gửi API khi rời ô / Enter / dừng gõ 600ms (tránh gọi API mỗi phím). */
function QtyInput({ line, onCommit }: { line: CartLine; onCommit: (qty: number) => void }) {
  const [draft, setDraft] = useState(String(line.quantity));
  const max = Math.max(1, Math.min(999, line.item.availableQuantity || 1));

  useEffect(() => setDraft(String(line.quantity)), [line.quantity]);

  const commit = (raw: string) => {
    const qty = Math.max(1, Math.min(max, Math.floor(Number(raw)) || 1));
    setDraft(String(qty));
    if (qty !== line.quantity) onCommit(qty);
  };

  useEffect(() => {
    if (draft === String(line.quantity)) return;
    const t = setTimeout(() => commit(draft), 600);
    return () => clearTimeout(t);
    // commit đọc draft hiện tại; chỉ chạy lại khi draft đổi
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draft]);

  return (
    <input
      type="number"
      className="prm tb"
      min={1}
      max={max}
      value={draft}
      disabled={!line.item.isActive}
      onChange={(e) => setDraft(e.target.value)}
      onBlur={(e) => commit(e.target.value)}
      onKeyDown={(e) => e.key === "Enter" && (e.preventDefault(), commit((e.target as HTMLInputElement).value))}
    />
  );
}

export function CartLines({
  lines,
  unchecked,
  setUnchecked,
  onQuantity,
  onRemove,
  onFavorite,
  subtotal,
  count,
}: {
  lines: CartLine[];
  unchecked: number[];
  setUnchecked: (fn: (u: number[]) => number[]) => void;
  onQuantity: (id: number, qty: number) => void;
  onRemove: (id: number) => void;
  onFavorite: (line: CartLine) => void;
  subtotal: number;
  count: number;
}) {
  return (
    <table border={0} cellPadding={0} cellSpacing={0} id="TableCart">
      <thead>
        <tr>
          <th>
            <input
              type="checkbox"
              checked={unchecked.length === 0}
              onChange={(e) => setUnchecked(() => (e.target.checked ? [] : lines.map((l) => l.id)))}
            />
          </th>
          <th colSpan={2}>Sản phẩm</th>
          <th>Đơn giá</th>
          <th>Số lượng</th>
          <th>Thành tiền (VNĐ)</th>
          <th>Thao tác</th>
        </tr>
      </thead>
      <tbody>
        {lines.map((l, i) => {
          const problem = lineProblem(l);
          const a = l.item.availableQuantity;
          return (
            <tr key={l.id} style={{ verticalAlign: "middle" }} className={`item${i}`}>
              <td className="cot0-2">
                <div className="btn-action">
                  <input
                    type="checkbox"
                    checked={!unchecked.includes(l.id)}
                    onChange={(e) => setUnchecked((u) => (e.target.checked ? u.filter((k) => k !== l.id) : [...u, l.id]))}
                  />
                </div>
              </td>
              <td className="cot0">
                <Link href={hrefOf(l)}>{l.item.primaryImageUrl && <img alt={l.item.name} width={120} src={l.item.primaryImageUrl} />}</Link>
              </td>
              <td className="cot0-1">
                <Link className="itemName" href={hrefOf(l)}>
                  {l.item.name}
                </Link>
                <div className="info">
                  <div className="info-text">{l.item.type === "COMBO" ? "Combo" : l.item.brand || ""}</div>
                </div>
                {problem && <div className="note text-danger">{problem}</div>}
              </td>
              <td align="center">
                <div className="itemPrice">{vnd(l.item.price)}</div>
                {l.item.originalPrice > l.item.price && (
                  <del style={{ color: "#999", fontSize: 12 }}>{vnd(l.item.originalPrice)}</del>
                )}
              </td>
              <td align="center">
                <QtyInput line={l} onCommit={(qty) => onQuantity(l.id, qty)} />
                {!problem && a > 0 && a <= LOW_STOCK && <div className="note text-danger">Sắp bán hết (chỉ còn {a} sp)</div>}
              </td>
              <td className="cot7" align="center">
                {vnd(l.lineTotal)}
              </td>
              <td align="center">
                <div className="btn-action">
                  {l.item.type === "PRODUCT" && (
                    <span className="nav" onClick={() => onFavorite(l)}>
                      Yêu thích
                    </span>
                  )}
                  <span className="nav" onClick={() => onRemove(l.id)}>
                    Xóa
                  </span>
                </div>
              </td>
            </tr>
          );
        })}
      </tbody>
      <tfoot>
        <tr>
          <td colSpan={5} align="center">
            Tạm tính (<span id="ItemInCart">{count}</span> sản phẩm)
          </td>
          <td align="center" id="cartAmount">
            {vnd(subtotal)}
          </td>
        </tr>
      </tfoot>
    </table>
  );
}
