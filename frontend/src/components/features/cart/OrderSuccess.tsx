"use client";
/* eslint-disable @next/next/no-img-element */
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { vnd } from "@/lib/format";
import { getErrorMessage } from "@/lib/errorMessages";
import { isApiError } from "@/services/apiError";
import { getOrder } from "@/services/orderService";
import { useShop } from "@/components/features/shop/ShopProvider";
import type { OrderDetail } from "@/types/api/order";

/** Số lần hỏi lại khi đơn VNPay còn PENDING (IPN chưa về — user-flows mục 4). */
const POLL_TIMES = 5;
const POLL_MS = 2000;

type Load = { status: "loading" } | { status: "ready"; order: OrderDetail } | { status: "error"; message: string; notFound: boolean };

function headline(o: OrderDetail): { icon: string; title: string; text: string; ok: boolean } {
  if (o.status === "PAYMENT_FAILED") {
    return { icon: "!", title: "Thanh toán không thành công", ok: false,
      text: "Giao dịch VNPay chưa hoàn tất nên đơn hàng đã được huỷ và sản phẩm đã trả lại kho. Bạn có thể đặt lại đơn mới." };
  }
  if (o.status === "CANCELLED") {
    return { icon: "!", title: "Đơn hàng đã huỷ", ok: false, text: o.history.at(-1)?.note ?? "Đơn hàng đã bị huỷ." };
  }
  if (o.paymentMethod === "VNPAY" && o.payment.status !== "SUCCESS") {
    return { icon: "…", title: "Đang xác nhận thanh toán", ok: true,
      text: "Chúng tôi đang chờ VNPay xác nhận giao dịch. Trang sẽ tự cập nhật trong giây lát." };
  }
  if (o.paymentMethod === "VNPAY") {
    return { icon: "✓", title: "Thanh toán thành công!", ok: true, text: "Đơn hàng đã được xác nhận và đang được chuẩn bị giao." };
  }
  return { icon: "✓", title: "Đặt hàng thành công!", ok: true,
    text: `Nhân viên sẽ gọi xác nhận qua số ${o.recipientPhone}. Bạn thanh toán khi nhận hàng.` };
}

/** Kết quả đặt hàng (`/checkout/success?orderId=`) — đọc đơn thật từ API (GET /orders/{id}). */
export function OrderSuccess() {
  const orderId = Number(useSearchParams().get("orderId"));
  const { ready, user, openLogin } = useShop();
  const [load, setLoad] = useState<Load>({ status: "loading" });

  useEffect(() => {
    if (!ready || !user || !orderId) return;
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const fetchOrder = (attempt: number) => {
      getOrder(orderId)
        .then((order) => {
          if (!active) return;
          setLoad({ status: "ready", order });
          const waiting = order.paymentMethod === "VNPAY" && order.status === "PENDING" && order.payment.status === "PENDING";
          if (waiting && attempt < POLL_TIMES) timer = setTimeout(() => fetchOrder(attempt + 1), POLL_MS);
        })
        .catch((err: unknown) => {
          if (!active) return;
          setLoad({ status: "error", message: getErrorMessage(err), notFound: isApiError(err) && err.status === 404 });
        });
    };
    fetchOrder(0);
    return () => {
      active = false;
      if (timer) clearTimeout(timer);
    };
  }, [ready, user, orderId]);

  if (!ready) return <div className="pageContent" style={{ minHeight: 400 }} />;

  if (!user) {
    return (
      <div className="pageContent cv-success">
        <h1>Vui lòng đăng nhập</h1>
        <p>Đăng nhập để xem đơn hàng của bạn.</p>
        <div className="btns">
          <button type="button" className="cv-btn" onClick={openLogin}>
            Đăng nhập
          </button>
        </div>
      </div>
    );
  }

  if (!orderId || load.status === "error") {
    return (
      <div className="pageContent cv-success">
        <h1>{!orderId || (load.status === "error" && load.notFound) ? "Không tìm thấy đơn hàng" : "Không tải được đơn hàng"}</h1>
        {load.status === "error" && !load.notFound && <p>{load.message}</p>}
        <div className="btns">
          <Link className="cv-btn" href="/">
            Về trang chủ
          </Link>
        </div>
      </div>
    );
  }

  if (load.status === "loading") return <div className="pageContent" style={{ minHeight: 400 }} />;

  const o = load.order;
  const h = headline(o);
  return (
    <div className="pageContent cv-success">
      <div className="ic" style={h.ok ? undefined : { background: "#c7370f" }}>
        {h.icon}
      </div>
      <h1>{h.title}</h1>
      <p>
        Mã đơn hàng: <b>{o.orderCode}</b> — Tổng thanh toán: <b style={{ color: "#c7370f" }}>{vnd(o.finalAmount)}</b>
      </p>
      <p>{h.text}</p>
      <div className="box">
        {o.items.map((i) => (
          <div key={`${i.type}-${i.itemId}`} style={{ display: "flex", gap: 12, alignItems: "center", padding: "6px 0" }}>
            {i.imageUrl && <img src={i.imageUrl} alt={i.name} width={56} height={56} style={{ objectFit: "cover" }} />}
            <div style={{ flex: 1 }}>
              {i.name} <span style={{ color: "#777" }}>× {i.quantity}</span>
            </div>
            <div>{vnd(i.lineTotal)}</div>
          </div>
        ))}
        <hr />
        <div>Tạm tính: {vnd(o.subtotal)}</div>
        {o.discountAmount > 0 && <div>Giảm giá: -{vnd(o.discountAmount)}</div>}
        <div>Phí vận chuyển: {o.shippingFee ? vnd(o.shippingFee) : "Miễn phí"}</div>
        <div>
          Giao tới: {o.recipientName} – {o.recipientPhone} – {o.shippingAddress}
        </div>
        {o.shipping.trackingCode && (
          <div>
            Mã vận đơn {o.shipping.carrier}: <b>{o.shipping.trackingCode}</b>
          </div>
        )}
      </div>
      <div className="btns">
        <Link className="cv-btn ghost" href="/products">
          Tiếp tục mua sắm
        </Link>
        <Link className="cv-btn" href="/account">
          Xem đơn hàng của tôi
        </Link>
      </div>
    </div>
  );
}
