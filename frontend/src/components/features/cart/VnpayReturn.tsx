"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { getErrorMessage } from "@/lib/errorMessages";
import { confirmVnpayReturn } from "@/services/orderService";
import type { PaymentOutcome } from "@/types/api/order";

const OUTCOME_ERRORS: Partial<Record<PaymentOutcome, string>> = {
  INVALID_SIGNATURE: "Dữ liệu thanh toán không hợp lệ (sai chữ ký). Nếu tài khoản đã bị trừ tiền, vui lòng liên hệ cửa hàng.",
  ORDER_NOT_FOUND: "Không tìm thấy đơn hàng của giao dịch này.",
  AMOUNT_MISMATCH: "Số tiền thanh toán không khớp với đơn hàng. Vui lòng liên hệ cửa hàng để được hỗ trợ.",
};

/**
 * Return URL của VNPay (`/payment/vnpay-return`, D-28): chuyển nguyên query `vnp_*` cho backend kiểm chữ ký và ghi nhận
 * kết quả (dùng chung handlePaymentResult với IPN), rồi chuyển sang trang kết quả đơn hàng.
 */
export function VnpayReturn() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const started = useRef(false);

  useEffect(() => {
    // StrictMode (dev) chạy effect 2 lần — gọi lặp vẫn an toàn (idempotent) nhưng không cần thiết
    if (started.current) return;
    started.current = true;
    const query = window.location.search.replace(/^\?/, "");
    if (!query.includes("vnp_")) {
      setError("Thiếu thông tin giao dịch từ VNPay.");
      return;
    }
    confirmVnpayReturn(query)
      .then((r) => {
        if (r.orderId && !OUTCOME_ERRORS[r.outcome]) {
          router.replace(`/checkout/success?orderId=${r.orderId}`);
        } else {
          setError(OUTCOME_ERRORS[r.outcome] ?? "Không xác định được kết quả thanh toán.");
        }
      })
      .catch((err: unknown) => setError(getErrorMessage(err)));
  }, [router]);

  if (!error) {
    return (
      <div className="pageContent cv-success">
        <div className="ic">…</div>
        <h1>Đang xác nhận thanh toán</h1>
        <p>Vui lòng không đóng trang trong giây lát.</p>
      </div>
    );
  }
  return (
    <div className="pageContent cv-success">
      <div className="ic" style={{ background: "#c7370f" }}>
        !
      </div>
      <h1>Không xác nhận được thanh toán</h1>
      <p>{error}</p>
      <div className="btns">
        <Link className="cv-btn ghost" href="/cart">
          Về giỏ hàng
        </Link>
        <Link className="cv-btn" href="/account">
          Đơn hàng của tôi
        </Link>
      </div>
    </div>
  );
}
