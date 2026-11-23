"use client";
/* eslint-disable @next/next/no-img-element */

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { vnd } from "@/lib/format";
import { getErrorMessage } from "@/lib/errorMessages";
import { fmtDateTime } from "@/lib/orderStatus";
import {
  RETURN_IMAGE_TYPES,
  RETURN_MAX_DESCRIPTION,
  RETURN_MAX_IMAGE_BYTES,
  RETURN_MAX_IMAGES,
  RETURN_REASONS,
} from "@/lib/returns";
import { isApiError } from "@/services/apiError";
import { createReturn, getOrder } from "@/services/orderService";
import { useAuth } from "@/components/features/auth/AuthProvider";
import { useShop } from "@/components/features/shop/ShopProvider";
import type { OrderDetail, ReturnReason } from "@/types/api/order";

type Load = { status: "loading" } | { status: "ready"; order: OrderDetail } | { status: "error"; message: string };
type Picked = { file: File; url: string };

/** Lỗi gửi yêu cầu: câu của trường lỗi (thiếu ảnh, lý do...) hoặc câu theo mã lỗi. */
function submitError(e: unknown): string {
  if (isApiError(e)) {
    const field = Object.values(e.fieldErrors)[0];
    if (field) return field;
  }
  return getErrorMessage(e);
}

/** Bảng chọn lý do trượt từ dưới lên (giống app di động); chọn xong bấm "Xác nhận". */
function ReasonSheet({ value, onPick, onClose }: { value: ReturnReason | null; onPick: (r: ReturnReason) => void; onClose: () => void }) {
  const [draft, setDraft] = useState<ReturnReason | null>(value);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    document.addEventListener("keydown", onKey);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = overflow;
    };
  }, [onClose]);

  return (
    <div className="cv-sheet-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="cv-sheet" role="dialog" aria-modal="true" aria-labelledby="cv-reason-title">
        <div className="cv-sheet__hd">
          <span id="cv-reason-title">Chọn lý do</span>
          <button type="button" className="close" aria-label="Đóng" onClick={onClose}>
            ×
          </button>
        </div>
        <div className="cv-sheet__body" role="radiogroup" aria-labelledby="cv-reason-title">
          {RETURN_REASONS.map((r) => (
            <label key={r.value} className="cv-sheet__opt">
              <input type="radio" name="return-reason" checked={draft === r.value} onChange={() => setDraft(r.value)} />
              <span>{r.label}</span>
            </label>
          ))}
        </div>
        <div className="cv-sheet__ft">
          <button type="button" className="cv-btn" disabled={!draft} onClick={() => draft && onPick(draft)}>
            Xác nhận
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * /account/orders/[id]/return — khách gửi yêu cầu trả hàng / hoàn tiền (FR-19a, D-41): trả nguyên đơn; chọn lý do,
 * chụp hoặc tải 1–2 ảnh, mô tả thêm (không bắt buộc). Nhân viên duyệt là hoàn tất.
 */
export function ReturnRequestForm({ id }: { id: number }) {
  const router = useRouter();
  const { ready, user, openLogin, notify } = useShop();
  const { user: profile } = useAuth();
  const [load, setLoad] = useState<Load>({ status: "loading" });
  const [reason, setReason] = useState<ReturnReason | null>(null);
  const [sheetOpen, setSheetOpen] = useState(false);
  const [images, setImages] = useState<Picked[]>([]);
  const [description, setDescription] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
  // Giữ danh sách ảnh mới nhất để thu hồi object URL khi rời trang
  const imagesRef = useRef<Picked[]>([]);
  imagesRef.current = images;

  useEffect(() => () => imagesRef.current.forEach((p) => URL.revokeObjectURL(p.url)), []);

  const reload = useCallback(() => {
    getOrder(id)
      .then((order) => setLoad({ status: "ready", order }))
      .catch((e) => setLoad({ status: "error", message: getErrorMessage(e) }));
  }, [id]);

  useEffect(() => {
    if (ready && user) reload();
  }, [ready, user, reload]);

  const closeSheet = useCallback(() => setSheetOpen(false), []);

  if (!ready || (user && load.status === "loading")) return <div className="pageContent" style={{ minHeight: 400 }} />;
  if (!user) {
    return (
      <div className="pageContent cv-success">
        <h1>Vui lòng đăng nhập</h1>
        <div className="btns">
          <button type="button" className="cv-btn" onClick={openLogin}>
            Đăng nhập
          </button>
        </div>
      </div>
    );
  }
  if (load.status !== "ready") {
    return (
      <div className="pageContent cv-success">
        <h1>Không tải được đơn hàng</h1>
        <p>{load.status === "error" ? load.message : ""}</p>
        <div className="btns">
          <Link className="cv-btn" href="/account">
            Về đơn hàng của tôi
          </Link>
        </div>
      </div>
    );
  }

  const o = load.order;
  const back = `/account/orders/${o.id}`;
  if (!o.canReturn) {
    const why = o.returnRequest
      ? "Đơn hàng này đã có yêu cầu trả hàng."
      : o.status !== "COMPLETED"
        ? "Chỉ có thể yêu cầu trả hàng sau khi bạn đã nhận hàng (đơn hoàn tất)."
        : "Đơn hàng đã quá thời hạn trả hàng.";
    return (
      <div className="pageContent cv-success">
        <h1>Không thể trả hàng</h1>
        <p>{why}</p>
        <div className="btns">
          <Link className="cv-btn" href={back}>
            Xem đơn {o.orderCode}
          </Link>
        </div>
      </div>
    );
  }

  const refund = o.subtotal - o.discountAmount;
  const canSubmit = !!reason && images.length > 0 && !submitting;

  const addFiles = (list: FileList | null) => {
    if (!list) return;
    const room = RETURN_MAX_IMAGES - images.length;
    const files = Array.from(list);
    const ok: Picked[] = [];
    for (const f of files) {
      if (!RETURN_IMAGE_TYPES.includes(f.type)) notify(`"${f.name}" không phải ảnh JPG, PNG hoặc WEBP`);
      else if (f.size > RETURN_MAX_IMAGE_BYTES) notify(`"${f.name}" vượt quá 5MB`);
      else if (ok.length < room) ok.push({ file: f, url: URL.createObjectURL(f) });
    }
    if (files.length > room) notify(`Chỉ được gửi tối đa ${RETURN_MAX_IMAGES} ảnh`);
    if (ok.length) setImages((cur) => [...cur, ...ok]);
  };

  const removeImage = (idx: number) =>
    setImages((cur) => {
      URL.revokeObjectURL(cur[idx].url);
      return cur.filter((_, i) => i !== idx);
    });

  const submit = async () => {
    if (!canSubmit || !reason) return;
    setSubmitting(true);
    try {
      await createReturn(o.id, { reason, description: description.trim() || undefined, images: images.map((p) => p.file) });
      notify("Đã gửi yêu cầu trả hàng. Campvivo sẽ phản hồi sớm nhất.");
      router.push(back);
    } catch (e) {
      notify(submitError(e));
      // Đơn vừa có yêu cầu / hết hạn ở tab khác → tải lại để hiện đúng trạng thái
      if (isApiError(e) && e.errorCode.startsWith("RETURN_")) reload();
      setSubmitting(false);
    }
  };

  return (
    <div className="pageContent cv-return">
      <div className="cv-return__top">
        <Link href={back} className="cv-return__back" aria-label="Quay lại đơn hàng">
          ←
        </Link>
        <h1>Yêu cầu Trả hàng/Hoàn tiền</h1>
      </div>
      <p className="cv-return__hint">
        Đơn {o.orderCode} · Trả nguyên đơn · Hạn gửi yêu cầu: {fmtDateTime(o.returnDeadline)}
      </p>

      <section className="cv-return__card">
        {o.items.map((i) => (
          <div className="cv-return__item" key={`${i.type}-${i.itemId}-${i.variantId ?? ""}`}>
            {i.imageUrl ? <img src={i.imageUrl} alt="" /> : <span className="ph" />}
            <div className="nm">
              <div className="t">
                {i.name}
                {i.type === "COMBO" && <span className="muted"> (Combo)</span>}
              </div>
              {i.variantTitle && <div className="muted">{i.variantTitle}</div>}
              <div>
                {i.originalPrice > i.unitPrice && <del className="muted">{vnd(i.originalPrice)}</del>} {vnd(i.unitPrice)}
              </div>
            </div>
            <div className="qty">x{i.quantity}</div>
          </div>
        ))}
      </section>

      <section className="cv-return__card">
        <button type="button" className="cv-return__row link" onClick={() => setSheetOpen(true)}>
          <span>
            Lý do<b className="req">*</b>
          </span>
          <span className={reason ? "val" : "ph"}>
            {reason ? RETURN_REASONS.find((r) => r.value === reason)?.label : "Chọn lý do"} ›
          </span>
        </button>
        {!reason && <div className="cv-return__warn">Chọn 1 lý do</div>}
        <div className="cv-return__row">
          <span>
            Phương án<b className="req">*</b>
          </span>
          <span className="val">Trả hàng và hoàn tiền</span>
        </div>
      </section>

      <section className="cv-return__card">
        <div className="cv-return__row">
          <span>Tổng tiền hoàn</span>
          <b className="money">{vnd(refund)}</b>
        </div>
        <div className="cv-return__sub">
          <span>Giá sản phẩm sau khuyến mãi</span>
          <span>{vnd(refund)}</span>
        </div>
        <div className="cv-return__note">Phí vận chuyển không được hoàn. Campvivo hoàn tiền sau khi duyệt yêu cầu.</div>
      </section>

      <section className="cv-return__card">
        <div className="cv-return__row">
          <span>
            Hình ảnh sản phẩm<b className="req">*</b>
          </span>
          <span className="muted">
            {images.length}/{RETURN_MAX_IMAGES}
          </span>
        </div>
        <div className="cv-return__images">
          {images.map((p, idx) => (
            <div className="cv-return__thumb" key={p.url}>
              <img src={p.url} alt={`Ảnh ${idx + 1}`} />
              <button type="button" aria-label={`Xoá ảnh ${idx + 1}`} onClick={() => removeImage(idx)}>
                ×
              </button>
            </div>
          ))}
          {images.length < RETURN_MAX_IMAGES && (
            <button type="button" className="cv-return__add" onClick={() => fileInput.current?.click()}>
              <span aria-hidden="true">＋</span>
              Chụp / tải ảnh
            </button>
          )}
          <input
            ref={fileInput}
            type="file"
            accept={RETURN_IMAGE_TYPES.join(",")}
            multiple
            hidden
            onChange={(e) => {
              addFiles(e.target.files);
              e.target.value = "";
            }}
          />
        </div>
        {images.length === 0 && <div className="cv-return__warn">Chụp hoặc tải lên 1–{RETURN_MAX_IMAGES} ảnh cho thấy tình trạng sản phẩm</div>}
      </section>

      <section className="cv-return__card">
        <div className="cv-return__row">
          <label htmlFor="cv-return-desc">Mô tả</label>
          <span className="muted">
            {description.length}/{RETURN_MAX_DESCRIPTION}
          </span>
        </div>
        <textarea
          id="cv-return-desc"
          className="cv-return__desc"
          rows={4}
          maxLength={RETURN_MAX_DESCRIPTION}
          placeholder="Ghi chú thêm"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
      </section>

      <section className="cv-return__card">
        <div className="cv-return__row">
          <span>
            Email<b className="req">*</b>
          </span>
          <span className="val">{profile?.email ?? ""}</span>
        </div>
      </section>

      <div className="cv-return__submit">
        <button type="button" className="cv-btn" disabled={!canSubmit} onClick={() => void submit()}>
          {submitting ? "Đang gửi..." : "Gửi yêu cầu"}
        </button>
      </div>

      {sheetOpen && (
        <ReasonSheet
          value={reason}
          onClose={closeSheet}
          onPick={(r) => {
            setReason(r);
            setSheetOpen(false);
          }}
        />
      )}
    </div>
  );
}
