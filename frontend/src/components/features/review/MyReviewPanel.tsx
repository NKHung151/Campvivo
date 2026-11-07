"use client";

import { useState } from "react";
import type { ItemType } from "@/types/api/common";
import type { MyReview } from "@/types/api/review";
import { getErrorMessage } from "@/lib/errorMessages";
import { isApiError } from "@/services/apiError";
import { createReview, deleteReview, getMyReview, itemRef, updateReview } from "@/services/reviewService";
import { useShop } from "@/components/features/shop/ShopProvider";
import { ReviewForm } from "@/components/features/review/ReviewForm";
import { ReviewItem } from "@/components/features/review/ReviewItem";

export type ReviewChange = "created" | "updated" | "deleted";

/**
 * Đánh giá của chính khách cho một mặt hàng — dùng chung cho trang sản phẩm/combo và hộp thoại ở chi tiết đơn.
 * - `review = null` → form viết (component cha chỉ render khi khách viết được).
 * - Có đánh giá → xem + Sửa / Xoá (xoá hỏi xác nhận); đang mờ → ghi chú; bị ẩn → lý do, chỉ còn Xoá (D-31).
 * - Gửi mà server báo đã có đánh giá (REVIEW_ALREADY_EXISTS) → tải đánh giá đó và chuyển sang sửa.
 */
export function MyReviewPanel({
  itemType,
  itemId,
  review,
  onChange,
  startEditing,
}: {
  itemType: ItemType;
  itemId: number;
  review: MyReview | null;
  onChange: (next: MyReview | null, change: ReviewChange) => void;
  startEditing?: boolean;
}) {
  const { notify } = useShop();
  const [editing, setEditing] = useState(Boolean(startEditing));
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const create = async (rating: number, content: string | null) => {
    setBusy(true);
    setError(null);
    try {
      const created = await createReview({ ...itemRef(itemType, itemId), rating, content });
      notify("Cảm ơn bạn đã đánh giá!");
      onChange(created, "created");
    } catch (e) {
      if (isApiError(e) && e.errorCode === "REVIEW_ALREADY_EXISTS") {
        const existing = await getMyReview(itemRef(itemType, itemId)).catch(() => null);
        if (existing) {
          setEditing(!existing.isHidden);
          setError("Bạn đã đánh giá mặt hàng này trước đó — bạn có thể sửa đánh giá bên dưới.");
          onChange(existing, "updated");
          return;
        }
      }
      setError(getErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const update = async (rating: number, content: string | null) => {
    if (!review) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await updateReview(review.id, { rating, content });
      notify("Đã cập nhật đánh giá");
      setEditing(false);
      onChange(updated, "updated");
    } catch (e) {
      setError(getErrorMessage(e));
      // Vừa bị nhân viên ẩn → tải lại để hiện lý do, chỉ còn nút Xoá
      if (isApiError(e) && e.errorCode === "REVIEW_HIDDEN") {
        const fresh = await getMyReview(itemRef(itemType, itemId)).catch(() => null);
        setEditing(false);
        onChange(fresh, fresh ? "updated" : "deleted");
      }
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (!review) return;
    setBusy(true);
    setError(null);
    try {
      await deleteReview(review.id);
      notify("Đã xoá đánh giá");
      setConfirmDelete(false);
      setEditing(false);
      onChange(null, "deleted");
    } catch (e) {
      setError(getErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  if (!review) {
    return (
      <div className="cv-review-mine">
        <div className="cv-review-mine__title">Đánh giá của bạn</div>
        <ReviewForm submitLabel="Gửi đánh giá" submitting={busy} error={error} onSubmit={create} />
      </div>
    );
  }

  if (editing && !review.isHidden) {
    return (
      <div className="cv-review-mine">
        <div className="cv-review-mine__title">Sửa đánh giá của bạn</div>
        <ReviewForm
          key={review.id}
          initialRating={review.rating}
          initialContent={review.content ?? ""}
          submitLabel="Lưu đánh giá"
          submitting={busy}
          error={error}
          onSubmit={update}
          onCancel={() => {
            setEditing(false);
            setError(null);
          }}
        />
      </div>
    );
  }

  return (
    <div className="cv-review-mine">
      <div className="cv-review-mine__title">Đánh giá của bạn</div>
      {review.isHidden ? (
        <div className="cv-review-note danger">
          Đánh giá của bạn đã bị ẩn{review.hiddenReason ? `: ${review.hiddenReason}` : "."} Bạn chỉ có thể xoá đánh giá này và
          viết lại.
        </div>
      ) : (
        review.isSensitive && (
          <div className="cv-review-note">
            Đánh giá của bạn đang bị làm mờ với người khác vì chứa ngôn từ không phù hợp. Bạn có thể sửa lại nội dung.
          </div>
        )
      )}
      <ReviewItem review={review} own hideName />
      {error && (
        <div className="cv-review-form">
          <div className="err" role="alert">
            {error}
          </div>
        </div>
      )}
      {confirmDelete ? (
        <div className="actions" role="group" aria-label="Xác nhận xoá">
          <span style={{ alignSelf: "center" }}>Xoá đánh giá này?</span>
          <button type="button" className="cv-link-btn danger" disabled={busy} onClick={remove}>
            {busy ? "Đang xoá…" : "Xoá"}
          </button>
          <button type="button" className="cv-link-btn" disabled={busy} onClick={() => setConfirmDelete(false)}>
            Không
          </button>
        </div>
      ) : (
        <div className="actions">
          {!review.isHidden && (
            <button
              type="button"
              className="cv-link-btn"
              onClick={() => {
                setEditing(true);
                setError(null);
              }}
            >
              Sửa
            </button>
          )}
          <button type="button" className="cv-link-btn danger" onClick={() => setConfirmDelete(true)}>
            Xoá
          </button>
        </div>
      )}
    </div>
  );
}
