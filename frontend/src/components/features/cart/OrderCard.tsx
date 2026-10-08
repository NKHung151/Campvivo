/* eslint-disable @next/next/no-img-element */
import Link from "next/link";
import type { Order } from "@/types/shop";
import { productHref, vnd } from "@/lib/format";

export function OrderCard({ order }: { order: Order }) {
  const c = order.customer;
  return (
    <div className="cv-order">
      <div className="hd">
        <span>
          Đơn <b>#{order.id}</b> · {new Date(order.date).toLocaleString("vi-VN")}
        </span>
        <span>{order.status}</span>
      </div>
      {order.items.map((i) => (
        <div className="row" key={i.key}>
          <img src={i.img} alt="" />
          <div className="nm">
            <Link href={productHref(i.slug)}>{i.name}</Link>
            {i.variant && <div style={{ color: "#777", fontSize: 13 }}>{i.variant}</div>}
          </div>
          <div>x{i.qty}</div>
          <div style={{ width: 120, textAlign: "right" }}>{vnd(i.price * i.qty)}</div>
        </div>
      ))}
      <div className="row" style={{ display: "block", fontSize: 13, color: "#555" }}>
        Giao tới: {c.gender} {c.name} – {c.phone} – {[c.street, c.ward, c.district, c.province].filter(Boolean).join(", ")}
        <br />
        Thanh toán: {order.payment} · Vận chuyển: {order.shipping}
      </div>
      <div className="ft">
        Tạm tính {vnd(order.subtotal)}
        {order.voucher ? ` · Voucher -${vnd(order.voucher)}` : ""}
        {order.member ? ` · Member -${vnd(order.member)}` : ""}
        {order.points ? ` · WePoint -${vnd(order.points)}` : ""} · Tổng tiền <b>{vnd(order.total)}</b>
      </div>
    </div>
  );
}
