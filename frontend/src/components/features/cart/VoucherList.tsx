"use client";
/* eslint-disable @next/next/no-img-element */

import { useEffect, useState } from "react";
import { vnd } from "@/lib/format";
import { getErrorMessage } from "@/lib/errorMessages";
import { discountLabel, expiryLabel, minOrderLabel } from "@/lib/promotion";
import { getAvailableVouchers } from "@/services/promotionService";
import type { AvailableVoucher } from "@/types/api/promotion";

type ListState =
  | { status: "loading" }
  | { status: "ready"; vouchers: AvailableVoucher[] }
  | { status: "error"; message: string };

function reasonText(v: AvailableVoucher): string {
  if (v.reason === "VOUCHER_MIN_ORDER_NOT_MET" && v.missingAmount) return `Mua thêm ${vnd(v.missingAmount)} để dùng mã này`;
  if (v.reason === "VOUCHER_ALREADY_USED") return "Bạn đã dùng mã này";
  return "Chưa dùng được";
}

/**
 * Danh sách mã công khai trong popup voucher trang /cart (D-29). Tải lại khi popup mở hoặc giỏ/dòng được chọn đổi.
 * Mã dùng được có nút Áp dụng (mã giảm nhiều nhất gắn nhãn "Tốt nhất"); mã chưa dùng được hiện mờ kèm lý do.
 */
export function VoucherList({
  open,
  cartItemIds,
  cartVersion,
  appliedCode,
  onApply,
}: {
  open: boolean;
  cartItemIds: number[];
  /** Đổi khi giỏ đổi (số lượng/giá) → tính lại danh sách. */
  cartVersion: string;
  appliedCode: string | null;
  onApply: (code: string) => void;
}) {
  const [state, setState] = useState<ListState>({ status: "loading" });
  const [reloadKey, setReloadKey] = useState(0);
  const idsKey = cartItemIds.join(",");

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setState((s) => (s.status === "ready" ? s : { status: "loading" }));
    getAvailableVouchers(idsKey ? idsKey.split(",").map(Number) : undefined)
      .then((vouchers) => !cancelled && setState({ status: "ready", vouchers }))
      .catch((e) => !cancelled && setState({ status: "error", message: getErrorMessage(e) }));
    return () => {
      cancelled = true;
    };
  }, [open, idsKey, cartVersion, reloadKey]);

  if (state.status === "loading") return <div className="cvVoucherHint">Đang tải mã giảm giá...</div>;
  if (state.status === "error") {
    return (
      <div className="cvVoucherHint">
        Không tải được danh sách mã. {state.message}{" "}
        <a href="#" onClick={(e) => (e.preventDefault(), setReloadKey((k) => k + 1))}>
          Thử lại
        </a>
      </div>
    );
  }
  if (!state.vouchers.length) return <div className="cvVoucherHint">Hiện chưa có mã giảm giá nào. Bạn vẫn có thể nhập mã ở ô phía trên.</div>;

  return (
    <div className="cvVoucherList">
      <div className="voucher_title">Mã giảm giá dành cho bạn</div>
      {state.vouchers.map((v) => {
        const applied = appliedCode === v.code;
        return (
          <div key={v.code} className={`voucher_group${applied ? " selected" : ""}${v.usable ? "" : " is-disabled"}`}>
            <div className="item_voucher">
              <div className="voucher_icon">
                <img src="/assets/css/icon/promotion.svg" alt="" />
                <span>{v.code}</span>
              </div>
              <div className="voucher_info-container">
                <div className="voucher-info">
                  <div className="voucher-title">
                    {discountLabel(v)}
                    {v.best && <span className="cvVoucherBest">Tốt nhất</span>}
                  </div>
                  <div className="voucher-description">
                    {minOrderLabel(v.minOrderValue)} · {expiryLabel(v.expiryDate)}
                    {v.remaining <= 10 && ` · Còn ${v.remaining} lượt`}
                    <br />
                    {v.usable ? (
                      <>
                        Giảm <b>{vnd(v.estimatedDiscount)}</b> cho đơn này
                      </>
                    ) : (
                      <span className="cvVoucherReason">{reasonText(v)}</span>
                    )}
                  </div>
                </div>
                {v.usable && (
                  <button type="button" className="cvVoucherApply" disabled={applied} onClick={() => onApply(v.code)}>
                    {applied ? "Đang dùng" : "Áp dụng"}
                  </button>
                )}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
