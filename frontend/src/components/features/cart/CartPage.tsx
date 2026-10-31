"use client";
/* eslint-disable @next/next/no-img-element */

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import type { Customer } from "@/types/shop";
import type { OrderPreview } from "@/types/api/order";
import type { PaymentMethod } from "@/types/api/common";
import { vnd } from "@/lib/format";
import { getErrorMessage } from "@/lib/errorMessages";
import { isApiError } from "@/services/apiError";
import { createOrder } from "@/services/orderService";
import { useShop } from "@/components/features/shop/ShopProvider";
import { Chevron, PinIcon } from "@/components/features/cart/cartIcons";
import { CartLines, lineProblem } from "@/components/features/cart/CartLines";
import { DeliveryInfoModal, EMPTY_CUSTOMER, fullAddress, hasGhnAddress } from "@/components/features/cart/DeliveryInfoModal";
import { useOrderPreview, type OrderPreviewState } from "@/components/features/cart/useOrderPreview";

const PAYMENTS: { id: PaymentMethod; label: string; desc: string }[] = [
  { id: "COD", label: "Thanh toán khi nhận hàng (COD)", desc: "Nhân viên gọi xác nhận, bạn trả tiền mặt cho người giao hàng." },
  { id: "VNPAY", label: "Thanh toán online qua VNPay (thẻ ATM, QR, thẻ quốc tế)", desc: "Bạn sẽ được chuyển sang cổng VNPay để thanh toán. Đơn chưa thanh toán sẽ tự huỷ sau 15 phút." },
];

/** Lỗi voucher (BR-5) — hiện ở ô mã giảm giá, không chặn đặt hàng (bỏ mã là đặt được). */
const VOUCHER_ERRORS = ["INVALID_VOUCHER", "VOUCHER_EXPIRED", "VOUCHER_MIN_ORDER_NOT_MET", "VOUCHER_EXHAUSTED", "VOUCHER_ALREADY_USED"];
/** Lỗi do giỏ đổi giữa chừng — tải lại giỏ để khách thấy dòng có vấn đề. */
const CART_ERRORS = ["OUT_OF_STOCK", "PRODUCT_INACTIVE", "CART_EMPTY", "NOT_FOUND"];

function shippingLabel(s: OrderPreviewState): string {
  if (s.status === "loading") return "Đang tính...";
  if (s.status === "error") return VOUCHER_ERRORS.includes(s.error.errorCode) ? "—" : "Không tính được";
  if (s.status !== "ready") return "—";
  if (!s.preview.shippingCalculated) return "Chọn địa chỉ để tính phí";
  return s.preview.freeShipping ? "Miễn phí" : vnd(s.preview.shippingFee);
}

export function CartPage({ csvcHtml, pvcHtml }: { csvcHtml: string; pvcHtml: string }) {
  const shop = useShop();
  const { cart, cartStatus, ready, user, canBuy, customer } = shop;
  const router = useRouter();
  const [unchecked, setUnchecked] = useState<number[]>([]);
  const [payment, setPayment] = useState<PaymentMethod>("COD");
  const [infoOpen, setInfoOpen] = useState(false);
  const [saveAsDefault, setSaveAsDefault] = useState(false);
  const [voucherOpen, setVoucherOpen] = useState(false);
  const [code, setCode] = useState("");
  const [voucherCode, setVoucherCode] = useState<string | null>(null);
  const [voucherMsg, setVoucherMsg] = useState("");
  const [popup, setPopup] = useState<"csvc" | "pvc" | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const lines = useMemo(() => cart?.items ?? [], [cart]);
  const selected = lines.filter((l) => !unchecked.includes(l.id));
  const deliverTo = hasGhnAddress(customer) ? customer : null;
  // Điền sẵn form: địa chỉ lần trước, không có thì tên + SĐT từ hồ sơ
  const initialForm: Customer = customer ?? { ...EMPTY_CUSTOMER, name: user?.name ?? "", phone: user?.phone ?? "" };

  const { state: previewState, retry: retryPreview } = useOrderPreview({
    enabled: ready && canBuy,
    cartItemIds: selected.map((l) => l.id),
    districtId: deliverTo?.districtId,
    wardCode: deliverTo?.wardCode,
    voucherCode,
    cartVersion: lines.map((l) => `${l.id}:${l.quantity}:${l.item.price}`).join("|"),
  });

  // Mã giảm giá không dùng được → báo ở ô mã và bỏ mã để vẫn tính được tổng
  useEffect(() => {
    if (previewState.status === "error" && voucherCode && VOUCHER_ERRORS.includes(previewState.error.errorCode)) {
      setVoucherMsg(previewState.error.message);
      setVoucherCode(null);
    } else if (previewState.status === "ready" && previewState.preview.voucherCode) {
      setVoucherMsg(`Áp dụng thành công: giảm ${vnd(previewState.preview.discountAmount)}`);
    }
  }, [previewState, voucherCode]);

  // Bỏ chọn những dòng đã bị xoá khỏi giỏ
  useEffect(() => {
    setUnchecked((u) => u.filter((id) => lines.some((l) => l.id === id)));
  }, [lines]);

  const preview: OrderPreview | null =
    previewState.status === "ready" ? previewState.preview : previewState.status === "idle" ? null : previewState.last;
  const count = selected.reduce((n, l) => n + l.quantity, 0);
  const subtotal = preview?.subtotal ?? selected.reduce((n, l) => n + l.lineTotal, 0);

  if (!ready || (canBuy && (cartStatus === "idle" || (cartStatus === "loading" && !cart)))) {
    return <div className="pageContent" style={{ minHeight: 400 }} />;
  }

  const header = (
    <div className="cartHeader">
      <h1 className="cartTitle">Giỏ hàng của bạn</h1>
      <Link className="hv_underline" href="/products">
        Mua thêm sản phẩm khác <Chevron color="#2f7abb" />
      </Link>
    </div>
  );

  // Giỏ hàng chỉ dành cho khách hàng đã đăng nhập (FR-09)
  if (!canBuy || cartStatus === "error" || !lines.length) {
    return (
      <div className="pageContent">
        <div className="cb h20" />
        {header}
        {!user ? (
          <p>
            Vui lòng{" "}
            <a href="#" className="hv_underline" onClick={(e) => (e.preventDefault(), shop.openLogin())}>
              đăng nhập
            </a>{" "}
            để xem giỏ hàng và đặt hàng.
          </p>
        ) : !canBuy ? (
          <p>Tài khoản nhân viên/quản trị không dùng để mua hàng. Vui lòng đăng nhập bằng tài khoản khách hàng.</p>
        ) : cartStatus === "error" ? (
          <p>
            Không tải được giỏ hàng.{" "}
            <a href="#" className="hv_underline" onClick={(e) => (e.preventDefault(), void shop.refreshCart())}>
              Thử lại
            </a>
          </p>
        ) : (
          <p>Chưa có sản phẩm nào trong giỏ hàng của bạn.</p>
        )}
        <div className="cb h20" />
      </div>
    );
  }

  const applyVoucher = () => {
    const v = code.trim().toUpperCase();
    if (!v) return setVoucherMsg("Vui lòng nhập mã giảm giá.");
    setVoucherMsg("Đang kiểm tra mã...");
    setVoucherCode(v);
  };

  const confirmInfo = (c: Customer) => {
    shop.saveCustomer(c);
    setInfoOpen(false);
  };

  const placeOrder = async (expectedTotal: number): Promise<void> => {
    if (!deliverTo) return;
    setSubmitting(true);
    try {
      const res = await createOrder({
        recipientName: deliverTo.name,
        recipientPhone: deliverTo.phone,
        shippingAddress: fullAddress(deliverTo),
        shippingDistrictId: deliverTo.districtId,
        shippingWardCode: deliverTo.wardCode,
        saveAsDefault,
        paymentMethod: payment,
        voucherCode: voucherCode ?? undefined,
        note: deliverTo.note.trim() || undefined,
        expectedTotal,
        cartItemIds: selected.map((l) => l.id),
      });
      if (res.paymentUrl) {
        // Rời trang sang cổng VNPay; giỏ đã được dọn ở server
        window.location.assign(res.paymentUrl);
        return;
      }
      await shop.refreshCart();
      router.push(`/checkout/success?orderId=${res.orderId}`);
    } catch (err) {
      if (isApiError(err) && err.errorCode === "PRICE_CHANGED") {
        const fresh = err.details as unknown as OrderPreview | undefined;
        if (fresh && window.confirm(`${err.message}\nTổng thanh toán mới: ${vnd(fresh.finalAmount)}. Bạn có muốn tiếp tục đặt hàng?`)) {
          return placeOrder(fresh.finalAmount);
        }
        retryPreview();
      } else if (isApiError(err) && CART_ERRORS.includes(err.errorCode)) {
        shop.notify(err.message);
        await shop.refreshCart();
      } else if (isApiError(err) && VOUCHER_ERRORS.includes(err.errorCode)) {
        setVoucherMsg(err.message);
        setVoucherCode(null);
        shop.notify(err.message);
      } else {
        shop.notify(getErrorMessage(err));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const checkout = (e: React.FormEvent) => {
    e.preventDefault();
    if (submitting) return;
    if (!selected.length) return shop.notify("Vui lòng chọn ít nhất 1 sản phẩm.");
    const problem = selected.find((l) => lineProblem(l));
    if (problem) return shop.notify(`"${problem.item.name}": ${lineProblem(problem)}. Vui lòng sửa giỏ hàng trước khi đặt.`);
    if (!deliverTo) return setInfoOpen(true);
    if (previewState.status === "loading") return shop.notify("Đang tính tiền, vui lòng đợi giây lát.");
    if (previewState.status !== "ready" || !previewState.preview.shippingCalculated) {
      return shop.notify("Chưa tính được tổng tiền. Vui lòng kiểm tra lại địa chỉ giao hàng.");
    }
    void placeOrder(previewState.preview.finalAmount);
  };

  const previewError = previewState.status === "error" && !VOUCHER_ERRORS.includes(previewState.error.errorCode) ? previewState.error : null;

  return (
    <>
      <div className="pageContent">
        <div className="cb h20" />
        {header}
        <div className="info-cart__location show" onClick={() => setInfoOpen(true)}>
          <div className="info-cart__info">
            <div className="info-cart__label">
              <a className="info-cart__locationName">
                {customer ? `${customer.gender} ${customer.name} - ${customer.phone}` : "Vui lòng cung cấp thông tin nhận hàng"}
              </a>
            </div>
            <div className="info-cart__boxLocation">
              <span className="iconcart-label-location">
                <PinIcon />
              </span>
              <div className="info-cart-textLocation">
                {customer ? fullAddress(customer) : "Bạn chưa chọn địa chỉ"}
                {customer && !deliverTo && <div className="text-danger">Vui lòng chọn lại Tỉnh/Quận/Phường để tính phí vận chuyển.</div>}
              </div>
            </div>
          </div>
          <div className="delivery_arrow">
            <Chevron color="#2f7abb" />
          </div>
        </div>
        <form id="ProductCart" onSubmit={checkout}>
          <div id="pnCart" className="BuyNowFrame">
            <CartLines
              lines={lines}
              unchecked={unchecked}
              setUnchecked={setUnchecked}
              onQuantity={(id, qty) => void shop.updateCartItem(id, qty)}
              onRemove={(id) => void shop.removeCartItem(id)}
              onFavorite={(l) => {
                if (!shop.wishlist.includes(l.item.slug)) shop.toggleWishlist(l.item.slug);
                shop.notify("Đã thêm vào danh sách yêu thích");
              }}
              subtotal={selected.reduce((n, l) => n + l.lineTotal, 0)}
              count={count}
            />
            <div className="vien" />
            <div className="cartVoucher" onClick={() => setVoucherOpen(true)}>
              <div className="text-cartVoucher">
                <img src="/assets/css/icon/promotion.svg" alt="icon" /> Giảm giá từ Campvivo
                {voucherCode && <b style={{ marginLeft: 8, color: "#03a804" }}>{voucherCode}</b>}
              </div>
              <div className="icon-nav-cartVoucher">
                <Chevron />
              </div>
            </div>
            <div className={`popup-voucher${voucherOpen ? " active" : ""}`}>
              <div className="head-popup-voucher">
                <div className="title-popup-voucher">Giảm giá từ Campvivo</div>
                <div className="close-btn-popup-voucher" onClick={() => setVoucherOpen(false)}>
                  ×
                </div>
              </div>
              <div className="saleCode">
                <button type="button" className="btnApply" onClick={applyVoucher}>
                  Áp dụng
                </button>
                <input type="text" id="tbVoucherCode" maxLength={50} placeholder="Điền mã giảm giá" value={code} onChange={(e) => setCode(e.target.value)} />
                <div className="cb" />
                <div id="VoucherNotification">
                  {voucherMsg}
                  {voucherCode && (
                    <>
                      {" "}
                      <a href="#" onClick={(e) => (e.preventDefault(), setVoucherCode(null), setVoucherMsg(""))}>
                        Bỏ mã
                      </a>
                    </>
                  )}
                </div>
              </div>
              <div className="cb h20" />
              <button type="button" className="btn-aplly-voucher" onClick={() => setVoucherOpen(false)}>
                Xác nhận
              </button>
            </div>
            <div className={`overlay-voucher-cart${voucherOpen ? " active" : ""}`} onClick={() => setVoucherOpen(false)} />
            <div className="cb h25" />
            <div className="step step2">
              <span className="num">1</span>Phương thức thanh toán
            </div>
            {PAYMENTS.map((p) => (
              <div className="item" key={p.id}>
                <input id={`rb${p.id}`} type="radio" name="rbHinhThucThanhToan" checked={payment === p.id} onChange={() => setPayment(p.id)} />{" "}
                <label htmlFor={`rb${p.id}`}>{p.label}</label>
                {payment === p.id && (
                  <div className="khungThongTin" style={{ display: "block" }}>
                    {p.desc}
                  </div>
                )}
              </div>
            ))}
            <div className="cb h10" />
            <div className="step step3">
              <span className="num">2</span>Xác nhận &amp; đặt hàng
            </div>
            <div id="option">
              <div className="leftOp">
                <div className="cb" />
                <div className="tablePrice">
                  <div className="tablePriceColumn tablePriceLeft">
                    <span>Tạm tính</span>
                    <span>Voucher</span>
                    <a id="phivanchuyen" className="hv_underline" href="#" onClick={(e) => (e.preventDefault(), setPopup("pvc"))}>
                      Phí vận chuyển{" "}
                      <svg width="14" height="14" viewBox="0 0 24 24" aria-hidden="true">
                        <circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" strokeWidth="2" />
                        <path d="M12 8v1M12 11v5" stroke="currentColor" strokeWidth="2" />
                      </svg>
                    </a>
                    <span>Tổng tiền</span>
                  </div>
                  <div className="tablePriceColumn tablePriceRight">
                    <span id="SubTotal">{vnd(subtotal)}</span>
                    <span id="VoucherDiscount">{preview?.discountAmount ? `-${vnd(preview.discountAmount)}` : 0}</span>
                    <span id="Ship" title={previewError?.message}>
                      {shippingLabel(previewState)}
                    </span>
                    <span id="cartTotalMoney">{preview && previewState.status === "ready" ? vnd(preview.finalAmount) : "—"}</span>
                  </div>
                </div>
                {previewError && (
                  <div className="note text-danger">
                    {previewError.message}{" "}
                    <a href="#" onClick={(e) => (e.preventDefault(), retryPreview())}>
                      Thử lại
                    </a>
                  </div>
                )}
                {preview?.shippingCalculated && !preview.freeShipping && (
                  <div className="note">Miễn phí vận chuyển cho đơn từ {vnd(preview.freeShippingThreshold)}.</div>
                )}
              </div>
              <div className="rightOp">
                <span>
                  <b>Bạn cần trợ giúp?</b> Chúng tôi sẵn sàng hỗ trợ bạn 24/7.
                </span>
                <span className="hotline">
                  Gọi ngay: <b>19000000</b>
                </span>
                <div className="cb h15" />
                <a href="#" id="ChinhSachVanChuyen" className="ChinhSachVanChuyen hv_underline" onClick={(e) => (e.preventDefault(), setPopup("csvc"))}>
                  Chính sách vận chuyển tại CAMPVIVO.VN
                </a>
              </div>
            </div>
            <div className="cartButton">
              <button type="submit" className="muangay" disabled={submitting}>
                {submitting ? "ĐANG ĐẶT HÀNG..." : payment === "VNPAY" ? "ĐẶT HÀNG & THANH TOÁN" : "ĐẶT HÀNG"}
              </button>
              <div>
                Nhấn &quot;ĐẶT HÀNG&quot; đồng nghĩa với việc bạn đồng ý tuân theo{" "}
                <Link className="hv_underline" target="_blank" href="/pages/dieu-khoan-va-quy-dinh-chung">
                  Điều khoản Campvivo
                </Link>
              </div>
            </div>
          </div>
        </form>
        <div className="cb h20" />
      </div>

      {popup && (
        <>
          <div id="light_bg" style={{ display: "block" }} onClick={() => setPopup(null)} />
          <div
            id={popup === "csvc" ? "popupCSVC" : "popupPVC"}
            style={{ display: "block" }}
            onClick={(e) => {
              if ((e.target as HTMLElement).closest(".closePopup")) {
                e.preventDefault();
                setPopup(null);
              }
            }}
            dangerouslySetInnerHTML={{ __html: popup === "csvc" ? csvcHtml : pvcHtml }}
          />
        </>
      )}

      <DeliveryInfoModal
        open={infoOpen}
        initial={initialForm}
        saveAsDefault={saveAsDefault}
        onSaveAsDefault={setSaveAsDefault}
        onClose={() => setInfoOpen(false)}
        onConfirm={confirmInfo}
      />
    </>
  );
}
