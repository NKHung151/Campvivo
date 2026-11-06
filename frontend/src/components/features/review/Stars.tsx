"use client";

import { useRef } from "react";

/** "4,5" — số sao kiểu Việt Nam (dấu phẩy thập phân). */
export function fmtRating(value: number): string {
  return value.toLocaleString("vi-VN", { minimumFractionDigits: 1, maximumFractionDigits: 1 });
}

/** Sao hiển thị theo thang 0–5 (có lẻ). `null` = chưa có đánh giá → 5 sao trống. */
export function StarRatingDisplay({ value, large }: { value: number | null; large?: boolean }) {
  const pct = value === null ? 0 : Math.max(0, Math.min(5, value)) * 20;
  return (
    <span
      className={`cv-stars${large ? " lg" : ""}`}
      role="img"
      aria-label={value === null ? "Chưa có đánh giá" : `${fmtRating(value)} trên 5 sao`}
    >
      <span style={{ width: `${pct}%` }} />
    </span>
  );
}

const LABELS = ["", "Rất tệ", "Tệ", "Bình thường", "Tốt", "Rất tốt"];

const StarIcon = () => (
  <svg width="100%" height="100%" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
    <path d="M12 2l3.1 6.3 6.9 1-5 4.9 1.2 6.8L12 17.8 5.8 21l1.2-6.8-5-4.9 6.9-1z" />
  </svg>
);

/**
 * Chọn 1–5 sao: nhóm radio (role="radiogroup"), bấm chuột hoặc phím ← → ↑ ↓ / Home / End. Chỉ sao đang chọn nằm
 * trong thứ tự Tab (roving tabindex); chưa chọn thì sao đầu tiên nhận Tab.
 */
export function StarRatingInput({
  value,
  onChange,
  disabled,
  id,
}: {
  value: number;
  onChange: (v: number) => void;
  disabled?: boolean;
  id?: string;
}) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  const pick = (v: number) => {
    const n = Math.max(1, Math.min(5, v));
    onChange(n);
    refs.current[n - 1]?.focus();
  };
  return (
    <div className="cv-star-input" role="radiogroup" aria-label="Số sao" id={id}>
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          ref={(el) => {
            refs.current[n - 1] = el;
          }}
          type="button"
          role="radio"
          aria-checked={value === n}
          aria-label={`${n} sao – ${LABELS[n]}`}
          tabIndex={value === n || (value === 0 && n === 1) ? 0 : -1}
          className={n <= value ? "on" : ""}
          disabled={disabled}
          onClick={() => pick(n)}
          onKeyDown={(e) => {
            const next =
              e.key === "ArrowRight" || e.key === "ArrowUp"
                ? (value || 0) + 1
                : e.key === "ArrowLeft" || e.key === "ArrowDown"
                  ? (value || 2) - 1
                  : e.key === "Home"
                    ? 1
                    : e.key === "End"
                      ? 5
                      : null;
            if (next === null) return;
            e.preventDefault();
            pick(next);
          }}
        >
          <StarIcon />
        </button>
      ))}
      <span className="lbl" aria-hidden="true">
        {value ? LABELS[value] : "Chọn số sao"}
      </span>
    </div>
  );
}
