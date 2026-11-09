"use client";

import { useState } from "react";
import type { Review } from "@/types/api/review";
import { StarRatingDisplay } from "@/components/features/review/Stars";

export function fmtDate(iso: string): string {
  return new Date(iso).toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" });
}

/** Chủ đã sửa: `updatedAt` sau `createdAt` (nhân viên ẩn/hiện không đổi `updatedAt`). */
export function isEdited(r: Review): boolean {
  return new Date(r.updatedAt).getTime() > new Date(r.createdAt).getTime();
}

/**
 * Một đánh giá: họ tên, ngày, sao, nội dung, "(đã chỉnh sửa)". `isSensitive` → làm mờ cả nội dung + cảnh báo + nút
 * "Hiển thị" (D-33); họ tên, sao, ngày vẫn hiện. `own` = của chính người xem → không làm mờ (đã có ghi chú riêng).
 */
export function ReviewItem({ review, own, hideName }: { review: Review; own?: boolean; hideName?: boolean }) {
  const [revealed, setRevealed] = useState(false);
  const blurred = review.isSensitive && !own && !revealed;
  const text = review.content ? (
    <div className="text">{review.content}</div>
  ) : (
    <div className="text empty">Không có nhận xét.</div>
  );
  return (
    <div className="cv-review-item">
      <div className="meta">
        {!hideName && <span className="name">{review.userFullName}</span>}
        <StarRatingDisplay value={review.rating} />
        <span className="date">{fmtDate(review.createdAt)}</span>
        {isEdited(review) && <span className="edited">(đã chỉnh sửa)</span>}
      </div>
      {blurred ? (
        <div className={`cv-review-blur${(review.content?.length ?? 0) < 80 ? " short" : ""}`}>
          <div aria-hidden="true">{text}</div>
          <div className="veil">
            <span>Đánh giá này có thể chứa ngôn từ không phù hợp.</span>
            <button type="button" className="cv-link-btn" onClick={() => setRevealed(true)}>
              Hiển thị
            </button>
          </div>
        </div>
      ) : (
        text
      )}
    </div>
  );
}
