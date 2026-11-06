"use client";

import { useId, useState } from "react";
import { StarRatingInput } from "@/components/features/review/Stars";

export const MAX_CONTENT = 2000;

/**
 * Form viết/sửa đánh giá: sao bắt buộc (1–5), nhận xét tuỳ chọn ≤ 2000 ký tự (không có độ dài tối thiểu — BR-10).
 * `error` = lỗi từ API do component cha truyền xuống.
 */
export function ReviewForm({
  initialRating = 0,
  initialContent = "",
  submitLabel,
  submitting,
  error,
  onSubmit,
  onCancel,
}: {
  initialRating?: number;
  initialContent?: string;
  submitLabel: string;
  submitting: boolean;
  error?: string | null;
  onSubmit: (rating: number, content: string | null) => void;
  onCancel?: () => void;
}) {
  const [rating, setRating] = useState(initialRating);
  const [content, setContent] = useState(initialContent);
  const [missingRating, setMissingRating] = useState(false);
  const textId = useId();

  return (
    <form
      className="cv-review-form"
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        if (submitting) return;
        if (rating < 1) {
          setMissingRating(true);
          return;
        }
        onSubmit(rating, content.trim() ? content.trim() : null);
      }}
    >
      <StarRatingInput
        value={rating}
        disabled={submitting}
        onChange={(v) => {
          setRating(v);
          setMissingRating(false);
        }}
      />
      {missingRating && (
        <div className="err" role="alert">
          Vui lòng chọn số sao.
        </div>
      )}
      <label htmlFor={textId} style={{ display: "block", marginTop: 10 }}>
        Nhận xét (không bắt buộc)
      </label>
      <textarea
        id={textId}
        rows={4}
        maxLength={MAX_CONTENT}
        value={content}
        disabled={submitting}
        placeholder="Chia sẻ trải nghiệm của bạn về sản phẩm…"
        onChange={(e) => setContent(e.target.value)}
      />
      <div className="counter">
        {content.length}/{MAX_CONTENT}
      </div>
      {error && (
        <div className="err" role="alert">
          {error}
        </div>
      )}
      <div className="row">
        <button type="submit" className="cv-btn" disabled={submitting}>
          {submitting ? "Đang gửi…" : submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="cv-btn ghost" disabled={submitting} onClick={onCancel}>
            Huỷ
          </button>
        )}
      </div>
    </form>
  );
}
