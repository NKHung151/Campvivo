"use client";

import { useCallback, useEffect, useState } from "react";
import type { ItemType } from "@/types/api/common";
import type { MyReview, Review, ReviewSummary } from "@/types/api/review";
import { getErrorMessage } from "@/lib/errorMessages";
import { canReview, getMyReview, getReviewSummary, itemRef, listReviews } from "@/services/reviewService";
import { useShop } from "@/components/features/shop/ShopProvider";
import { ReviewItem } from "@/components/features/review/ReviewItem";
import { MyReviewPanel } from "@/components/features/review/MyReviewPanel";
import { StarRatingDisplay, fmtRating } from "@/components/features/review/Stars";

const PAGE_SIZE = 10;
const STARS = [5, 4, 3, 2, 1] as const;

type ListState = { status: "loading" } | { status: "error"; message: string } | { status: "ready"; items: Review[]; total: number; page: number };

/** Tổng quan: điểm trung bình + tổng lượt + thanh phân bố 5★→1★ (bấm để lọc — D-32). */
function ReviewSummaryBar({
  summary,
  active,
  onFilter,
}: {
  summary: ReviewSummary;
  active: number | null;
  onFilter: (rating: number | null) => void;
}) {
  const max = Math.max(1, summary.reviewCount);
  return (
    <div className="cv-review-summary">
      <div className="avg">
        <b>{summary.averageRating === null ? "–" : fmtRating(summary.averageRating)}</b>
        <StarRatingDisplay value={summary.averageRating} large />
        <small>{summary.reviewCount} đánh giá</small>
      </div>
      <div className="cv-review-bars">
        {STARS.map((s) => (
          <button
            key={s}
            type="button"
            className={active === s ? "active" : ""}
            aria-pressed={active === s}
            aria-label={`Lọc ${s} sao: ${summary.ratingCounts[s]} đánh giá`}
            onClick={() => onFilter(active === s ? null : s)}
          >
            <span>{s}★</span>
            <span className="bar">
              <span style={{ width: `${(summary.ratingCounts[s] / max) * 100}%` }} />
            </span>
            <span className="n">{summary.ratingCounts[s]}</span>
          </button>
        ))}
      </div>
    </div>
  );
}

/** Khu "đánh giá của bạn" theo người xem: Guest / nhân viên / khách chưa đủ điều kiện / form / đã viết. */
function MyReviewBox({
  itemType,
  itemId,
  onMine,
  onMutated,
}: {
  itemType: ItemType;
  itemId: number;
  /** Đánh giá hiện tại của người xem (để bỏ khỏi danh sách chung). Phải là hàm ổn định (useCallback). */
  onMine: (mine: MyReview | null) => void;
  /** Vừa gửi/sửa/xoá → tải lại tổng quan + danh sách. */
  onMutated: () => void;
}) {
  const { ready, user, canBuy, openLogin } = useShop();
  const [state, setState] = useState<
    { status: "loading" } | { status: "error"; message: string } | { status: "ready"; mine: MyReview | null; canWrite: boolean }
  >({ status: "loading" });

  const load = useCallback(async () => {
    setState({ status: "loading" });
    try {
      const ref = itemRef(itemType, itemId);
      const [mine, can] = await Promise.all([getMyReview(ref), canReview(ref)]);
      setState({ status: "ready", mine, canWrite: can });
      onMine(mine);
    } catch (e) {
      setState({ status: "error", message: getErrorMessage(e) });
    }
  }, [itemType, itemId, onMine]);

  useEffect(() => {
    if (ready && canBuy) void load();
  }, [ready, canBuy, load]);

  if (!ready) return null;
  if (!user) {
    return (
      <div className="cv-review-muted">
        <button type="button" className="cv-link-btn" onClick={openLogin}>
          Đăng nhập để đánh giá
        </button>
      </div>
    );
  }
  // Nhân viên / Admin chỉ xem
  if (!canBuy) return null;
  if (state.status === "loading") return <div className="cv-review-muted">Đang tải…</div>;
  if (state.status === "error") {
    return (
      <div className="cv-review-muted">
        {state.message}{" "}
        <button type="button" className="cv-link-btn" onClick={() => void load()}>
          Thử lại
        </button>
      </div>
    );
  }
  if (!state.mine && !state.canWrite) {
    return <div className="cv-review-muted">Bạn có thể đánh giá sau khi nhận hàng thành công.</div>;
  }
  return (
    <MyReviewPanel
      itemType={itemType}
      itemId={itemId}
      review={state.mine}
      onChange={(next, change) => {
        // Xoá xong vẫn còn đơn đã mua → viết lại được ngay
        setState({ status: "ready", mine: next, canWrite: change === "deleted" ? true : state.canWrite });
        onMine(next);
        onMutated();
      }}
    />
  );
}

/**
 * Khối đánh giá dùng chung cho trang sản phẩm và combo (FR-18): tổng quan + lọc sao + danh sách mới nhất trước
 * ("Xem thêm" 10/lần) + đánh giá của người xem. Gửi/sửa/xoá → tải lại tổng quan và danh sách.
 */
export function ReviewSection({ itemType, itemId }: { itemType: ItemType; itemId: number }) {
  const [summary, setSummary] = useState<ReviewSummary | null>(null);
  const [filter, setFilter] = useState<number | null>(null);
  const [list, setList] = useState<ListState>({ status: "loading" });
  const [loadingMore, setLoadingMore] = useState(false);
  const [myId, setMyId] = useState<number | null>(null);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let alive = true;
    getReviewSummary(itemType, itemId)
      .then((s) => alive && setSummary(s))
      .catch(() => alive && setSummary(null));
    return () => {
      alive = false;
    };
  }, [itemType, itemId, version]);

  useEffect(() => {
    let alive = true;
    setList({ status: "loading" });
    listReviews(itemType, itemId, { rating: filter ?? undefined, page: 1, limit: PAGE_SIZE })
      .then((p) => alive && setList({ status: "ready", items: p.data, total: p.pagination.totalItems, page: 1 }))
      .catch((e) => alive && setList({ status: "error", message: getErrorMessage(e) }));
    return () => {
      alive = false;
    };
  }, [itemType, itemId, filter, version]);

  const more = async () => {
    if (list.status !== "ready" || loadingMore) return;
    setLoadingMore(true);
    try {
      const next = await listReviews(itemType, itemId, { rating: filter ?? undefined, page: list.page + 1, limit: PAGE_SIZE });
      setList({
        status: "ready",
        // Đánh giá mới chen vào đầu trong lúc xem → bỏ trùng theo id
        items: [...list.items, ...next.data.filter((r) => !list.items.some((x) => x.id === r.id))],
        total: next.pagination.totalItems,
        page: list.page + 1,
      });
    } catch {
      /* giữ danh sách hiện có; người dùng bấm lại */
    } finally {
      setLoadingMore(false);
    }
  };

  const onMineChanged = useCallback((mine: MyReview | null) => setMyId(mine?.id ?? null), []);
  const reload = useCallback(() => setVersion((v) => v + 1), []);

  // Đánh giá của người xem đã hiện ở khu riêng → không lặp lại trong danh sách
  const shown = list.status === "ready" ? list.items.filter((r) => r.id !== myId) : [];

  return (
    <div className="cv-reviews">
      <h2>Đánh giá của khách hàng</h2>
      {summary && <ReviewSummaryBar summary={summary} active={filter} onFilter={setFilter} />}
      <MyReviewBox itemType={itemType} itemId={itemId} onMine={onMineChanged} onMutated={reload} />
      <div className="cv-review-filters" role="group" aria-label="Lọc theo số sao">
        <button type="button" className={filter === null ? "active" : ""} aria-pressed={filter === null} onClick={() => setFilter(null)}>
          Tất cả
        </button>
        {STARS.map((s) => (
          <button key={s} type="button" className={filter === s ? "active" : ""} aria-pressed={filter === s} onClick={() => setFilter(s)}>
            {s}★
          </button>
        ))}
      </div>
      {list.status === "loading" && <div className="cv-review-muted">Đang tải đánh giá…</div>}
      {list.status === "error" && (
        <div className="cv-review-muted">
          Không tải được đánh giá: {list.message}{" "}
          <button type="button" className="cv-link-btn" onClick={() => setVersion((v) => v + 1)}>
            Thử lại
          </button>
        </div>
      )}
      {list.status === "ready" && shown.length === 0 && (
        <div className="cv-review-muted">
          {filter ? `Chưa có đánh giá ${filter} sao.` : list.items.length ? "Chưa có đánh giá nào khác." : "Chưa có đánh giá nào. Hãy là người đầu tiên!"}
        </div>
      )}
      {shown.map((r) => (
        <ReviewItem key={r.id} review={r} />
      ))}
      {list.status === "ready" && list.items.length < list.total && (
        <div style={{ textAlign: "center", marginTop: 10 }}>
          <button type="button" className="cv-btn ghost" disabled={loadingMore} onClick={() => void more()}>
            {loadingMore ? "Đang tải…" : "Xem thêm đánh giá"}
          </button>
        </div>
      )}
    </div>
  );
}
