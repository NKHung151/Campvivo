"use client";

import { useEffect, useState } from "react";
import { previewOrder } from "@/services/orderService";
import { ApiError, CLIENT_ERROR_CODES, isApiError } from "@/services/apiError";
import type { OrderPreview } from "@/types/api/order";

export type OrderPreviewState =
  | { status: "idle" }
  | { status: "loading"; last: OrderPreview | null }
  | { status: "ready"; preview: OrderPreview }
  | { status: "error"; error: ApiError; last: OrderPreview | null };

interface Input {
  enabled: boolean;
  cartItemIds: number[];
  districtId?: number;
  wardCode?: string;
  voucherCode: string | null;
  /** Đổi khi giỏ đổi (số lượng, dòng) để tính lại. */
  cartVersion: string;
}

/**
 * Bảng tiền do server tính (POST /orders/preview — api.md 4.5): tạm tính, giảm giá, phí ship GHN (D-27), tổng.
 * Tự tính lại khi đổi dòng chọn, số lượng, địa chỉ, mã giảm giá; chờ 300ms để gộp thay đổi liên tiếp, bỏ kết quả cũ.
 * `finalAmount` của kết quả là `expectedTotal` khi đặt hàng.
 */
export function useOrderPreview({ enabled, cartItemIds, districtId, wardCode, voucherCode, cartVersion }: Input) {
  const [state, setState] = useState<OrderPreviewState>({ status: "idle" });
  const [attempt, setAttempt] = useState(0);
  const idsKey = cartItemIds.join(",");

  useEffect(() => {
    if (!enabled || !idsKey) {
      setState({ status: "idle" });
      return;
    }
    let active = true;
    setState((s) => ({ status: "loading", last: s.status === "ready" ? s.preview : s.status === "idle" ? null : s.last }));
    const timer = setTimeout(() => {
      previewOrder({
        cartItemIds: idsKey.split(",").map(Number),
        shippingDistrictId: districtId,
        shippingWardCode: wardCode,
        voucherCode: voucherCode ?? undefined,
      })
        .then((preview) => active && setState({ status: "ready", preview }))
        .catch((err: unknown) => {
          if (!active) return;
          const error = isApiError(err)
            ? err
            : new ApiError({ status: 0, errorCode: CLIENT_ERROR_CODES.UNKNOWN_ERROR, message: "Có lỗi xảy ra." });
          setState((s) => ({ status: "error", error, last: s.status === "loading" ? s.last : null }));
        });
    }, 300);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [enabled, idsKey, districtId, wardCode, voucherCode, cartVersion, attempt]);

  return { state, retry: () => setAttempt((n) => n + 1) };
}
