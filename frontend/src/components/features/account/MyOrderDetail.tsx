"use client";
/* eslint-disable @next/next/no-img-element */

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { vnd } from "@/lib/format";
import { getErrorMessage } from "@/lib/errorMessages";
import { ORDER_STATUS, PAYMENT_METHOD_LABEL, PAYMENT_STATUS, fmtDateTime } from "@/lib/orderStatus";
import { RETURN_STATUS } from "@/lib/returns";
import { isApiError } from "@/services/apiError";
import { cancelOrder, getOrder } from "@/services/orderService";
import { useShop } from "@/components/features/shop/ShopProvider";
import { ReviewDialog, type ReviewDialogItem } from "@/components/features/review/ReviewDialog";
import type { OrderDetail } from "@/types/api/order";

type Load = { status: "loading" } | { status: "ready"; order: OrderDetail } | { status: "error"; message: string; notFound: boolean };

/**
 * /account/orders/[id] — chi tiết đơn của khách (FR-12): dòng hàng, tiền, người nhận, vận đơn, lịch sử; huỷ khi PENDING;
 * trả hàng khi COMPLETED trong hạn (FR-19a) và trạng thái yêu cầu đã gửi.
 */
export function MyOrderDetail({ id }: { id: number }) {
  const { ready, user, openLogin, notify } = useShop();
  const [load, setLoad] = useState<Load>({ status: "loading" });
  const [cancelOpen, setCancelOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [cancelling, setCancelling] = useState(false);
  /** Dòng hàng đang mở hộp thoại đánh giá (FR-18). */
  const [reviewing, setReviewing] = useState<(ReviewDialogItem & { hasReview: boolean }) | null>(null);

  const reload = useCallback(() => {
    getOrder(id)
      .then((order) => setLoad({ status: "ready", order }))
      .catch((e) => setLoad({ status: "error", message: getErrorMessage(e), notFound: isApiError(e) && e.status === 404 }));
  }, [id]);

  useEffect(() => {
    if (ready && user) reload();
  }, [ready, user, reload]);

  if (!ready) return <div className="pageContent" style={{ minHeight: 400 }} />;
  if (!user) {
    return (
      <div className="pageContent cv-success">
        <h1>Vui lòng đăng nhập</h1>
        <div className="btns">
          <button type="button" className="cv-btn" onClick={openLogin}>
            Đăng nhập
          </button>
        </div>
      </div>
    );
  }
  if (load.status === "loading") return <div className="pageContent" style={{ minHeight: 400 }} />;
  if (load.status === "error") {
    return (
      <div className="pageContent cv-success">
        <h1>{load.notFound ? "Không tìm thấy đơn hàng" : "Không tải được đơn hàng"}</h1>
        {!load.notFound && <p>{load.message}</p>}
        <div className="btns">
          <Link className="cv-btn" href="/account">
            Về đơn hàng của tôi
          </Link>
        </div>
      </div>
    );
  }

  const o = load.order;
  const completed = o.status === "COMPLETED";

  /** Cập nhật nút tại chỗ: mọi dòng cùng mặt hàng nhận id đánh giá mới (null = vừa xoá → viết lại được). */
  const applyReview = (type: string, itemId: number, reviewId: number | null) =>
    setLoad({
      status: "ready",
      order: {
        ...o,
        items: o.items.map((i) =>
          i.type === type && i.itemId === itemId ? { ...i, myReviewId: reviewId, canReview: completed && reviewId === null } : i,
        ),
      },
    });

  const doCancel = async () => {
    setCancelling(true);
    try {
      const updated = await cancelOrder(o.id, reason.trim() || undefined);
      setLoad({ status: "ready", order: updated });
      setCancelOpen(false);
      notify("Đã huỷ đơn hàng");
    } catch (e) {
      notify(getErrorMessage(e));
      // Đơn vừa được xác nhận/thanh toán → tải lại để thấy trạng thái mới
      if (isApiError(e) && e.errorCode === "ORDER_NOT_CANCELLABLE") reload();
    } finally {
      setCancelling(false);
    }
  };

  return (
    <div className="pageContent" style={{ padding: "24px 0 60px" }}>
      <p>
        <Link href="/account" className="hv_underline">
          ← Đơn hàng của tôi
        </Link>
      </p>
      <h1 style={{ fontSize: 22, margin: "8px 0 16px" }}>
        Đơn {o.orderCode}{" "}
        <span style={{ color: ORDER_STATUS[o.status].color, fontSize: 16 }}>· {ORDER_STATUS[o.status].label}</span>
      </h1>

      <div className="cv-order">
        <div className="hd">
          <span>Đặt lúc {fmtDateTime(o.createdAt)}</span>
          <span>
            {PAYMENT_METHOD_LABEL[o.paymentMethod]} · {PAYMENT_STATUS[o.payment.status].label}
          </span>
        </div>
        {o.items.map((i) => (
          <div className="row" key={`${i.type}-${i.itemId}-${i.variantId ?? ""}`}>
            {i.imageUrl && <img src={i.imageUrl} alt="" />}
            <div className="nm">
              {i.name}
              {i.type === "COMBO" && <span style={{ color: "#777" }}> (Combo)</span>}
              {i.variantTitle && <div style={{ color: "#777", fontSize: 13 }}>Phân loại: {i.variantTitle}</div>}
              <div style={{ color: "#777", fontSize: 13 }}>{vnd(i.unitPrice)} / sản phẩm</div>
              {completed && (
                <button
                  type="button"
                  className="cv-link-btn"
                  style={{ marginTop: 6 }}
                  onClick={() =>
                    setReviewing({ type: i.type, itemId: i.itemId, name: i.name, imageUrl: i.imageUrl, hasReview: i.myReviewId !== null })
                  }
                >
                  {i.myReviewId !== null ? "Xem đánh giá" : "Viết đánh giá"}
                </button>
              )}
            </div>
            <div>x{i.quantity}</div>
            <div style={{ width: 120, textAlign: "right" }}>{vnd(i.lineTotal)}</div>
          </div>
        ))}
        <div className="ft">
          Tạm tính {vnd(o.subtotal)}
          {o.discountAmount > 0 && ` · Giảm giá -${vnd(o.discountAmount)}`} · Phí vận chuyển {o.shippingFee ? vnd(o.shippingFee) : "Miễn phí"}
          <br />
          Tổng thanh toán <b>{vnd(o.finalAmount)}</b>
        </div>
      </div>

      <div className="cv-order">
        <div className="hd">
          <span>Giao hàng</span>
          <span>{o.shipping.trackingCode ? `Mã vận đơn ${o.shipping.carrier}: ${o.shipping.trackingCode}` : "Chưa có mã vận đơn"}</span>
        </div>
        <div className="row" style={{ display: "block" }}>
          {o.recipientName} – {o.recipientPhone}
          <br />
          {o.shippingAddress}
          {o.note && (
            <>
              <br />
              Ghi chú: {o.note}
            </>
          )}
        </div>
      </div>

      {o.returnRequest && (
        <div className="cv-order cv-return-status">
          <div className="hd">
            <span>Yêu cầu trả hàng</span>
            <b style={{ color: RETURN_STATUS[o.returnRequest.status].color }}>{RETURN_STATUS[o.returnRequest.status].label}</b>
          </div>
          <div className="row" style={{ display: "block" }}>
            Lý do: {o.returnRequest.reasonLabel} · Gửi lúc {fmtDateTime(o.returnRequest.createdAt)}
            {o.returnRequest.description && <div style={{ color: "#555", marginTop: 4 }}>{o.returnRequest.description}</div>}
            <div className="imgs">
              {o.returnRequest.imageUrls.map((url) => (
                <a key={url} href={url} target="_blank" rel="noreferrer">
                  <img src={url} alt="Ảnh trả hàng" />
                </a>
              ))}
            </div>
            <div style={{ marginTop: 8 }}>
              Số tiền hoàn: <b style={{ color: "#c7370f" }}>{vnd(o.returnRequest.refundAmount)}</b>
            </div>
            {o.returnRequest.status === "PENDING" && (
              <div style={{ color: "#777", fontSize: 13, marginTop: 4 }}>Campvivo đang xem xét yêu cầu của bạn.</div>
            )}
            {o.returnRequest.status === "APPROVED" && (
              <div style={{ color: "#777", fontSize: 13, marginTop: 4 }}>
                Yêu cầu đã được duyệt. Vui lòng gửi trả hàng; Campvivo sẽ liên hệ để hoàn tiền.
              </div>
            )}
            {o.returnRequest.note && (
              <div style={{ marginTop: 6 }}>
                Phản hồi của Campvivo: <i>{o.returnRequest.note}</i>
              </div>
            )}
          </div>
        </div>
      )}

      <div className="cv-order">
        <div className="hd">
          <span>Lịch sử đơn hàng</span>
        </div>
        {[...o.history].reverse().map((h, idx) => (
          <div className="row" key={idx} style={{ display: "block" }}>
            <b style={{ color: ORDER_STATUS[h.newStatus].color }}>{ORDER_STATUS[h.newStatus].label}</b> · {fmtDateTime(h.createdAt)}
            {h.note && <div style={{ color: "#555", fontSize: 13 }}>{h.note}</div>}
          </div>
        ))}
      </div>

      {o.canReturn && (
        <p style={{ display: "flex", flexWrap: "wrap", gap: 12, alignItems: "center" }}>
          <Link className="cv-btn ghost" href={`/account/orders/${o.id}/return`}>
            Trả hàng/Hoàn tiền
          </Link>
          {o.returnDeadline && <span style={{ color: "#777", fontSize: 13 }}>Hạn gửi yêu cầu: {fmtDateTime(o.returnDeadline)}</span>}
        </p>
      )}
      {o.canCancel && !cancelOpen && (
        <button type="button" className="cv-btn ghost" onClick={() => setCancelOpen(true)}>
          Huỷ đơn hàng
        </button>
      )}
      {cancelOpen && (
        <div className="cv-order" style={{ padding: 16 }}>
          <p style={{ marginTop: 0 }}>Bạn chắc chắn muốn huỷ đơn {o.orderCode}?</p>
          <textarea
            className="control"
            rows={3}
            maxLength={500}
            placeholder="Lý do huỷ (không bắt buộc)"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            style={{ width: "100%", marginBottom: 10 }}
          />
          <button type="button" className="cv-btn" disabled={cancelling} onClick={doCancel}>
            {cancelling ? "Đang huỷ..." : "Xác nhận huỷ"}
          </button>{" "}
          <button type="button" className="cv-btn ghost" disabled={cancelling} onClick={() => setCancelOpen(false)}>
            Không huỷ
          </button>
        </div>
      )}

      {reviewing && (
        <ReviewDialog
          item={reviewing}
          hasReview={reviewing.hasReview}
          onChanged={(reviewId) => applyReview(reviewing.type, reviewing.itemId, reviewId)}
          onClose={() => setReviewing(null)}
        />
      )}
    </div>
  );
}
