"use client";
/* eslint-disable @next/next/no-img-element */

import { useEffect, useRef, useState } from "react";
import type { ItemType } from "@/types/api/common";
import type { MyReview } from "@/types/api/review";
import { getErrorMessage } from "@/lib/errorMessages";
import { getMyReview, itemRef } from "@/services/reviewService";
import { MyReviewPanel } from "@/components/features/review/MyReviewPanel";

export interface ReviewDialogItem {
  type: ItemType;
  itemId: number;
  name: string;
  imageUrl: string | null;
}

/**
 * Hộp thoại đánh giá ở chi tiết đơn (FR-18): ảnh + tên mặt hàng, rồi `MyReviewPanel` — chưa có đánh giá → form viết;
 * có → xem + Sửa / Xoá (bị ẩn → chỉ Xoá). Esc / bấm nền / nút × để đóng. `onChanged` báo id đánh giá mới (null = đã xoá).
 */
export function ReviewDialog({
  item,
  hasReview,
  onChanged,
  onClose,
}: {
  item: ReviewDialogItem;
  hasReview: boolean;
  onChanged: (reviewId: number | null) => void;
  onClose: () => void;
}) {
  const [state, setState] = useState<
    { status: "loading" } | { status: "error"; message: string } | { status: "ready"; review: MyReview | null }
  >(hasReview ? { status: "loading" } : { status: "ready", review: null });
  const dialogRef = useRef<HTMLDivElement>(null);
  const titleId = `review-dialog-${item.type}-${item.itemId}`;

  useEffect(() => {
    if (!hasReview) return;
    let alive = true;
    getMyReview(itemRef(item.type, item.itemId))
      .then((review) => alive && setState({ status: "ready", review }))
      .catch((e) => alive && setState({ status: "error", message: getErrorMessage(e) }));
    return () => {
      alive = false;
    };
  }, [hasReview, item.type, item.itemId]);

  // onClose có thể đổi mỗi lần cha render → giữ trong ref để effect dưới chỉ chạy một lần khi mở
  const closeRef = useRef(onClose);
  closeRef.current = onClose;

  // Esc để đóng; đưa focus vào hộp thoại; khoá cuộn trang phía sau; đóng thì trả focus về nút đã mở
  useEffect(() => {
    const prev = document.activeElement as HTMLElement | null;
    dialogRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") closeRef.current();
    };
    document.addEventListener("keydown", onKey);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = overflow;
      prev?.focus?.();
    };
  }, []);

  return (
    <div
      className="cv-dialog-backdrop"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="cv-dialog" role="dialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1} ref={dialogRef}>
        <div className="hd">
          {item.imageUrl && <img src={item.imageUrl} alt="" />}
          <div className="nm" id={titleId}>
            {item.name}
            {item.type === "COMBO" && <span style={{ color: "#777", fontWeight: 400 }}> (Combo)</span>}
          </div>
          <button type="button" className="close" aria-label="Đóng" onClick={onClose}>
            ×
          </button>
        </div>
        {state.status === "loading" && <div className="cv-review-muted">Đang tải…</div>}
        {state.status === "error" && <div className="cv-review-muted">{state.message}</div>}
        {state.status === "ready" && (
          <MyReviewPanel
            itemType={item.type}
            itemId={item.itemId}
            review={state.review}
            onChange={(next) => {
              setState({ status: "ready", review: next });
              onChanged(next?.id ?? null);
            }}
          />
        )}
      </div>
    </div>
  );
}
