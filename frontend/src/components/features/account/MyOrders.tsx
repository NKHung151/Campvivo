"use client";
/* eslint-disable @next/next/no-img-element */

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { vnd } from "@/lib/format";
import { getErrorMessage } from "@/lib/errorMessages";
import { ORDER_STATUS, ORDER_STATUSES, PAYMENT_METHOD_LABEL, fmtDateTime } from "@/lib/orderStatus";
import { listMyOrders } from "@/services/orderService";
import type { OrderStatus } from "@/types/api/common";
import type { OrderSummary } from "@/types/api/order";

const PAGE_SIZE = 10;

/** Tab "Đơn hàng của tôi" (FR-12): lọc theo trạng thái, "Xem thêm" để tải trang sau. */
export function MyOrders() {
  const [status, setStatus] = useState<OrderStatus | "">("");
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    (p: number) => {
      setLoading(true);
      setError(null);
      listMyOrders({ status: status || undefined, page: p, limit: PAGE_SIZE })
        .then((res) => {
          setOrders((cur) => (p === 1 ? res.data : [...cur, ...res.data]));
          setPage(p);
          setTotalPages(res.pagination.totalPages);
        })
        .catch((e) => setError(getErrorMessage(e)))
        .finally(() => setLoading(false));
    },
    [status],
  );

  useEffect(() => load(1), [load]);

  return (
    <>
      <div style={{ marginBottom: 14 }}>
        <select className="select-single" value={status} onChange={(e) => setStatus(e.target.value as OrderStatus | "")} style={{ maxWidth: 260 }}>
          <option value="">Tất cả trạng thái</option>
          {ORDER_STATUSES.map((s) => (
            <option key={s} value={s}>
              {ORDER_STATUS[s].label}
            </option>
          ))}
        </select>
      </div>
      {error && (
        <div className="cv-empty">
          {error}{" "}
          <a href="#" onClick={(e) => (e.preventDefault(), load(1))}>
            Thử lại
          </a>
        </div>
      )}
      {!error && !loading && orders.length === 0 && (
        <div className="cv-empty">
          {status ? "Không có đơn hàng nào ở trạng thái này." : "Bạn chưa có đơn hàng nào."} <Link href="/products">Mua sắm ngay</Link>
        </div>
      )}
      {orders.map((o) => (
        <div className="cv-order" key={o.id}>
          <div className="hd">
            <span>
              Đơn <b>{o.orderCode}</b> · {fmtDateTime(o.createdAt)}
            </span>
            <span style={{ color: ORDER_STATUS[o.status].color, fontWeight: 600 }}>{ORDER_STATUS[o.status].label}</span>
          </div>
          <div className="row">
            {o.firstItemImageUrl && <img src={o.firstItemImageUrl} alt="" />}
            <div className="nm">
              {o.firstItemName}
              {o.itemCount > 1 && <div style={{ color: "#777", fontSize: 13 }}>và các sản phẩm khác · {o.itemCount} món</div>}
            </div>
            <div style={{ color: "#555", fontSize: 13 }}>{PAYMENT_METHOD_LABEL[o.paymentMethod]}</div>
          </div>
          <div className="ft">
            Tổng tiền <b>{vnd(o.finalAmount)}</b> ·{" "}
            <Link href={`/account/orders/${o.id}`} className="hv_underline">
              Xem chi tiết
            </Link>
          </div>
        </div>
      ))}
      {loading && <div className="cv-empty">Đang tải đơn hàng...</div>}
      {!loading && !error && page < totalPages && (
        <div style={{ textAlign: "center" }}>
          <button type="button" className="cv-btn ghost" onClick={() => load(page + 1)}>
            Xem thêm
          </button>
        </div>
      )}
    </>
  );
}
