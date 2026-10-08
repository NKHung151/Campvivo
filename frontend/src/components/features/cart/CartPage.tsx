"use client";
/* eslint-disable @next/next/no-img-element */

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import type { Customer } from "@/types/shop";
import { productHref, vnd } from "@/lib/format";
import { POINT_VALUE, useShop } from "@/components/features/shop/ShopProvider";
import { DISTRICTS } from "@/components/features/cart/geo";

const Chevron = ({ color = "#000" }: { color?: string }) => (
  <svg fill={color} height="10px" width="10px" viewBox="0 0 330 330" aria-hidden="true">
    <path d="M250.606,154.389l-150-149.996c-5.857-5.858-15.355-5.858-21.213,0.001 c-5.857,5.858-5.857,15.355,0.001,21.213l139.393,139.39L79.393,304.394c-5.857,5.858-5.857,15.355,0.001,21.213 C82.322,328.536,86.161,330,90,330s7.678-1.464,10.607-4.394l149.999-150.004c2.814-2.813,4.394-6.628,4.394-10.606 C255,161.018,253.42,157.202,250.606,154.389z" />
  </svg>
);
const PinIcon = () => (
  <svg width="16" height="16" viewBox="0 0 512 512" aria-hidden="true">
    <path
      fill="#2f7abb"
      d="M341.476 338.285c54.483-85.493 47.634-74.827 49.204-77.056C410.516 233.251 421 200.322 421 166 421 74.98 347.139 0 256 0 165.158 0 91 74.832 91 166c0 34.3 10.704 68.091 31.19 96.446l48.332 75.84C118.847 346.227 31 369.892 31 422c0 18.995 12.398 46.065 71.462 67.159C143.704 503.888 198.231 512 256 512c108.025 0 225-30.472 225-90 0-52.117-87.744-75.757-139.524-83.715zm-194.227-92.34a15.57 15.57 0 0 0-.517-.758C129.685 221.735 121 193.941 121 166c0-75.018 60.406-136 135-136 74.439 0 135 61.009 135 136 0 27.986-8.521 54.837-24.646 77.671-1.445 1.906 6.094-9.806-110.354 172.918L147.249 245.945zM256 482c-117.994 0-195-34.683-195-60 0-17.016 39.568-44.995 127.248-55.901l55.102 86.463a14.998 14.998 0 0 0 25.298 0l55.101-86.463C411.431 377.005 451 404.984 451 422c0 25.102-76.313 60-195 60z"
    />
    <path fill="#2f7abb" d="M256 91c-41.355 0-75 33.645-75 75s33.645 75 75 75 75-33.645 75-75-33.645-75-75-75zm0 120c-24.813 0-45-20.187-45-45s20.187-45 45-45 45 20.187 45 45-20.187 45-45 45z" />
  </svg>
);

const PAYMENTS = [
  { id: "rbCOD", label: "Thanh toán khi nhận hàng - COD" },
  { id: "rbChuyenKhoan", label: "Chuyển khoản qua ngân hàng" },
  { id: "rbAlePay", label: "Thanh toán online bằng thẻ ATM, IB, QRCODE, thẻ quốc tế hoặc thanh toán trả góp" },
];

// Demo voucher; the real site validates codes server-side.
const VOUCHERS: Record<string, { amount: number; min: number }> = { CAMPVIVO50: { amount: 50000, min: 300000 } };
const EMPTY_CUSTOMER: Customer = { gender: "Anh", name: "", phone: "", email: "", province: "", district: "", ward: "", street: "", note: "" };

export function CartPage({ csvcHtml, pvcHtml, provinces }: { csvcHtml: string; pvcHtml: string; provinces: { v: string; n: string }[] }) {
  const shop = useShop();
  const { cart, ready, user, customer, orders } = shop;
  const router = useRouter();
  const [unchecked, setUnchecked] = useState<string[]>([]);
  const [payment, setPayment] = useState(PAYMENTS[0].id);
  const [infoOpen, setInfoOpen] = useState(false);
  const [voucherOpen, setVoucherOpen] = useState(false);
  const [code, setCode] = useState("");
  const [voucher, setVoucher] = useState<{ code: string; amount: number } | null>(null);
  const [voucherMsg, setVoucherMsg] = useState("");
  const [usePoints, setUsePoints] = useState(false);
  const [popup, setPopup] = useState<"csvc" | "pvc" | null>(null);
  const [variantFor, setVariantFor] = useState<string | null>(null);
  const [form, setForm] = useState<Customer>(EMPTY_CUSTOMER);
  const [err, setErr] = useState("");

  useEffect(() => {
    // Prefill from the saved address (or the logged-in user's phone).
    if (ready) setForm(customer ?? { ...EMPTY_CUSTOMER, name: user?.name ?? "", phone: user?.phone ?? "" });
  }, [ready, customer, user]);

  const selected = cart.filter((c) => !unchecked.includes(c.key));
  const subtotal = selected.reduce((n, c) => n + c.price * c.qty, 0);
  const memberDiscount = user && orders.length > 0 ? Math.round(subtotal * 0.05) : 0;
  const voucherDiscount = voucher && subtotal >= (VOUCHERS[voucher.code]?.min ?? 0) ? voucher.amount : 0;
  const pointsAvail = (user?.points ?? 0) * POINT_VALUE;
  const pointsDiscount = usePoints ? Math.min(pointsAvail, Math.max(0, subtotal - memberDiscount - voucherDiscount)) : 0;
  const total = Math.max(0, subtotal - memberDiscount - voucherDiscount - pointsDiscount);
  const shipping = subtotal >= 1_000_000 ? "Miễn phí (tối đa 30.000 đ)" : "Tính theo ĐVVC";
  const count = selected.reduce((n, c) => n + c.qty, 0);
  const districts = useMemo(() => DISTRICTS[form.province] ?? null, [form.province]);
  const address = customer ? [customer.street, customer.ward, customer.district, customer.province].filter(Boolean).join(", ") : "";

  if (!ready) return <div className="pageContent" style={{ minHeight: 400 }} />;

  if (!cart.length) {
    return (
      <div className="pageContent">
        <div className="cb h20" />
        <div className="cartHeader">
          <h1 className="cartTitle">Giỏ hàng của bạn</h1>
          <Link className="hv_underline" href="/products">
            Mua thêm sản phẩm khác <Chevron color="#2f7abb" />
          </Link>
        </div>
        <p>Chưa có sản phẩm nào trong giỏ hàng của bạn.</p>
        <div className="cb h20" />
      </div>
    );
  }

  const set = (k: keyof Customer) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value, ...(k === "province" ? { district: "", ward: "" } : {}) }));

  const saveInfo = (e: React.FormEvent) => {
    e.preventDefault();
    if (!/^(0[35789])\d{8}$/.test(form.phone)) return setErr("Số điện thoại không hợp lệ (10 số, bắt đầu 03/05/07/08/09).");
    if (!form.province || !form.district || !form.street) return setErr("Vui lòng nhập đầy đủ địa chỉ nhận hàng.");
    setErr("");
    shop.saveCustomer(form);
    setInfoOpen(false);
  };

  const applyVoucher = () => {
    const v = VOUCHERS[code.trim().toUpperCase()];
    if (!v) return setVoucherMsg("Mã giảm giá không hợp lệ. (Bản demo: thử CAMPVIVO50)");
    if (subtotal < v.min) return setVoucherMsg(`Áp dụng cho đơn hàng tối thiểu ${vnd(v.min)}.`);
    setVoucher({ code: code.trim().toUpperCase(), amount: v.amount });
    setVoucherMsg(`Áp dụng thành công: giảm ${vnd(v.amount)}`);
  };

  const checkout = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selected.length) return shop.notify("Vui lòng chọn ít nhất 1 sản phẩm.");
    if (!customer) {
      setInfoOpen(true);
      return;
    }
    const order = shop.placeOrder({
      items: selected,
      subtotal,
      voucher: voucherDiscount,
      member: memberDiscount,
      points: pointsDiscount,
      shipping,
      total,
      payment: PAYMENTS.find((p) => p.id === payment)!.label,
      customer,
    });
    shop.clearCart(selected.map((c) => c.key));
    router.push(`/checkout/success?id=${order.id}`);
  };

  return (
    <>
      <div className="pageContent">
        <div className="cb h20" />
        <div className="cartHeader">
          <h1 className="cartTitle">Giỏ hàng của bạn</h1>
          <Link className="hv_underline" href="/products">
            Mua thêm sản phẩm khác <Chevron color="#2f7abb" />
          </Link>
        </div>
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
              <div className="info-cart-textLocation">{address || "Bạn chưa chọn địa chỉ"}</div>
            </div>
          </div>
          <div className="delivery_arrow">
            <Chevron color="#2f7abb" />
          </div>
        </div>
        <form id="ProductCart" onSubmit={checkout}>
          <div id="pnCart" className="BuyNowFrame">
            <table border={0} cellPadding={0} cellSpacing={0} id="TableCart">
              <thead>
                <tr>
                  <th>
                    <input
                      type="checkbox"
                      checked={unchecked.length === 0}
                      onChange={(e) => setUnchecked(e.target.checked ? [] : cart.map((c) => c.key))}
                    />
                  </th>
                  <th colSpan={2}>Sản phẩm</th>
                  <th>Đơn giá</th>
                  <th>Số lượng</th>
                  <th>Thành tiền (VNĐ)</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {cart.map((c, i) => {
                  const max = c.stock && c.stock > 0 ? c.stock : 1000;
                  return (
                    <tr key={c.key} style={{ verticalAlign: "middle" }} className={`item${i}`}>
                      <td className="cot0-2">
                        <div className="btn-action">
                          <input
                            type="checkbox"
                            checked={!unchecked.includes(c.key)}
                            onChange={(e) => setUnchecked((u) => (e.target.checked ? u.filter((k) => k !== c.key) : [...u, c.key]))}
                          />
                        </div>
                      </td>
                      <td className="cot0">
                        <Link href={productHref(c.slug)}>
                          <img alt={c.name} width={120} src={c.img} />
                        </Link>
                      </td>
                      <td className="cot0-1">
                        <Link className="itemName" href={productHref(c.slug)}>
                          {c.name}
                        </Link>
                        <div className="info" onClick={() => c.options && c.options.length > 1 && setVariantFor(variantFor === c.key ? null : c.key)}>
                          <div className="info-text">
                            SKU: {c.sku}
                            {c.variant ? ` - ${c.variantLabel || "Phân loại"}: ${c.variant}` : ""}
                          </div>
                          {c.options && c.options.length > 1 && (
                            <>
                              <div className={`variants-hotDeal${variantFor === c.key ? " show" : ""}`} onClick={(e) => e.stopPropagation()}>
                                <label className="hotDeal-optionName"> Phân loại</label>
                                <div className="list">
                                  {c.options.map((o) => (
                                    <button
                                      key={o}
                                      type="button"
                                      className={o === c.variant ? "active" : ""}
                                      onClick={() => {
                                        shop.setVariant(c.key, o);
                                        setVariantFor(null);
                                      }}
                                    >
                                      {o}
                                    </button>
                                  ))}
                                </div>
                              </div>
                              <svg width="10" height="10" viewBox="0 0 24 24" aria-hidden="true">
                                <path d="M7 10l5 5 5-5z" fill="#666" />
                              </svg>
                            </>
                          )}
                        </div>
                      </td>
                      <td align="center">
                        <div className="itemPrice">{vnd(c.price)}</div>
                      </td>
                      <td align="center">
                        <input
                          type="number"
                          className="prm tb"
                          min={1}
                          max={max}
                          value={c.qty}
                          onChange={(e) => shop.setQty(c.key, Math.min(max, +e.target.value || 1))}
                        />
                        {c.stock !== undefined && c.stock !== null && c.stock > 0 && c.stock <= 5 && (
                          <div className="note text-danger">Sắp bán hết (chỉ còn {c.stock} sp)</div>
                        )}
                      </td>
                      <td className="cot7" align="center">
                        {vnd(c.price * c.qty)}
                      </td>
                      <td align="center">
                        <div className="btn-action">
                          <span
                            className="nav"
                            onClick={() => {
                              if (!shop.wishlist.includes(c.slug)) shop.toggleWishlist(c.slug);
                              shop.notify("Đã thêm vào danh sách yêu thích");
                            }}
                          >
                            Yêu thích
                          </span>
                          <span className="nav" onClick={() => shop.removeFromCart(c.key)}>
                            Xóa
                          </span>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={5} align="center">
                    Tạm tính (<span id="ItemInCart">{count}</span> sản phẩm)
                  </td>
                  <td align="center" id="cartAmount">
                    {vnd(subtotal)}
                  </td>
                </tr>
              </tfoot>
            </table>
            <div className="vien" />
            <div className="cartVoucher" onClick={() => setVoucherOpen(true)}>
              <div className="text-cartVoucher">
                <img src="/assets/css/icon/promotion.svg" alt="icon" /> Giảm giá từ Campvivo
                {voucher && <b style={{ marginLeft: 8, color: "#03a804" }}>{voucher.code}</b>}
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
                <input type="text" id="tbVoucherCode" placeholder="Điền mã giảm giá" value={code} onChange={(e) => setCode(e.target.value)} />
                <div className="cb" />
                <div id="VoucherNotification">{voucherMsg}</div>
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
                <input id={p.id} type="radio" name="rbHinhThucThanhToan" checked={payment === p.id} onChange={() => setPayment(p.id)} />{" "}
                <label htmlFor={p.id}>{p.label}</label>
                {p.id === "rbChuyenKhoan" && (
                  <div className="khungThongTin" style={{ display: payment === p.id ? "block" : "none" }}>
                    Quý khách thanh toán qua ngân hàng, vui lòng chuyển tiền đến tài khoản của Campvivo theo thông tin dưới đây:
                    <br />
                    <div className="pl10 pb5">
                      {" "}
                      - Ngân hàng: <b>NGÂN HÀNG DEMO</b>
                      <br /> - Tên tài khoản: <b>CAMPVIVO DEMO</b>
                      <br /> - Số tài khoản: <b>0000 0000 00</b>
                      <br />
                    </div>
                    Đây là bản demo — <b>không chuyển tiền thật</b>.
                  </div>
                )}
                {p.id === "rbAlePay" && payment === p.id && (
                  <div className="khungThongTin" style={{ display: "block" }}>
                    Bản demo: đơn hàng sẽ được ghi nhận ở trạng thái &quot;Chờ thanh toán&quot;, không chuyển tới cổng thanh toán nào.
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
                    <span>Ưu đãi Member</span>
                    <div className="use-point">
                      <span>Dùng điểm quà tặng WePoint</span>
                      <label className="toggle-switch">
                        <input
                          type="checkbox"
                          id="useVipPoint"
                          checked={usePoints}
                          onChange={(e) => {
                            if (!user) {
                              shop.openLogin();
                              return;
                            }
                            setUsePoints(e.target.checked);
                          }}
                        />
                        <span className="slider" />
                      </label>
                    </div>
                    <a
                      id="phivanchuyen"
                      className="hv_underline"
                      href="#"
                      onClick={(e) => {
                        e.preventDefault();
                        setPopup("pvc");
                      }}
                    >
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
                    <span id="VoucherDiscount">{voucherDiscount ? `-${vnd(voucherDiscount)}` : 0}</span>
                    <span id="MemberDiscount">{memberDiscount ? `-${vnd(memberDiscount)}` : 0}</span>
                    <span id="PointDiscount">
                      {usePoints ? (
                        `-${vnd(pointsDiscount)}`
                      ) : (
                        <div className="point-customer">
                          Bạn có {(user?.points ?? 0).toLocaleString("de-DE")} điểm = {vnd(pointsAvail, " ₫")}
                        </div>
                      )}
                    </span>
                    <span id="Ship">{shipping}</span>
                    <span id="cartTotalMoney">{vnd(total)}</span>
                  </div>
                </div>
              </div>
              <div className="rightOp">
                <span>
                  <b>Bạn cần trợ giúp?</b> Chúng tôi sẵn sàng hỗ trợ bạn 24/7.
                </span>
                <span className="hotline">
                  Gọi ngay: <b>19000000</b>
                </span>
                <div className="cb h15" />
                <a
                  href="#"
                  id="ChinhSachVanChuyen"
                  className="ChinhSachVanChuyen hv_underline"
                  onClick={(e) => {
                    e.preventDefault();
                    setPopup("csvc");
                  }}
                >
                  Chính sách vận chuyển tại CAMPVIVO.VN
                </a>
              </div>
            </div>
            <div className="cartButton">
              <button type="submit" className="muangay">
                ĐẶT HÀNG
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

      <div className={`modal modal-info-cart${infoOpen ? " hi" : ""}`} onClick={(e) => e.target === e.currentTarget && setInfoOpen(false)}>
        <form id="infoCart" className="modal-box" onSubmit={saveInfo}>
          <div className="modal-header">
            <span>Thông tin giao hàng</span>
            <div className="close-btn" onClick={() => setInfoOpen(false)}>
              ×
            </div>
          </div>
          <div id="cartNotification" className="text-danger">
            {err}
          </div>
          <div className="cv-form">
            <div className="form-section-modal">
              <label className="label-title-modal">Thông tin người đặt</label>
              <div className="row">
                <div className="col-12">
                  {(["Anh", "Chị"] as const).map((g) => (
                    <span key={g}>
                      <input id={g === "Anh" ? "rbNam" : "rbNu"} type="radio" name="gender" checked={form.gender === g} onChange={() => setForm((f) => ({ ...f, gender: g }))} />{" "}
                      <label htmlFor={g === "Anh" ? "rbNam" : "rbNu"}>{g}</label>{" "}
                    </span>
                  ))}
                </div>
                <div className="col-12">
                  <div className="row">
                    <div className="col-6">
                      <input className="control" id="tbHoTenKH" type="text" required placeholder=" " value={form.name} onChange={set("name")} />
                      <label className="form-label" htmlFor="tbHoTenKH">
                        Họ và tên
                      </label>
                    </div>
                    <div className="col-6">
                      <input className="control" id="tbSoDienThoai" type="tel" maxLength={10} required placeholder=" " value={form.phone} onChange={set("phone")} />
                      <label className="form-label" htmlFor="tbSoDienThoai">
                        Số điện thoại
                      </label>
                    </div>
                    <div className="col-12">
                      <input className="control" id="tbEmail" type="email" required placeholder=" " value={form.email} onChange={set("email")} />
                      <label className="form-label" htmlFor="tbEmail">
                        Email
                      </label>
                    </div>
                  </div>
                </div>
              </div>
              {!user && (
                <div className="login-suggestion">
                  Hoặc{" "}
                  <a
                    href="#"
                    onClick={(e) => {
                      e.preventDefault();
                      shop.openLogin();
                    }}
                  >
                    Đăng nhập
                  </a>{" "}
                  để lấy nhanh thông tin giao hàng
                </div>
              )}
            </div>
            <div className="form-section-location">
              <div className="add-address">Thêm địa chỉ nhận hàng</div>
              <div className="row">
                <div className="col-12">
                  <div className="row">
                    <div className="col-6">
                      <select id="ddlProvince" className="select-single" value={form.province} onChange={set("province")} required>
                        <option value="">Chọn Tỉnh/Thành phố</option>
                        {provinces.map((p) => (
                          <option key={p.v} value={p.n}>
                            {p.n}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div className="col-6">
                      {districts ? (
                        <select id="ddlDistrict" className="select-single" value={form.district} onChange={set("district")} required>
                          <option value="">Chọn Quận/Huyện</option>
                          {districts.map((d) => (
                            <option key={d}>{d}</option>
                          ))}
                        </select>
                      ) : (
                        <input id="ddlDistrict" className="select-single" placeholder="Quận/Huyện" value={form.district} onChange={set("district")} required />
                      )}
                    </div>
                    <div className="col-6">
                      <input id="ddlWard" className="select-single" placeholder="Phường/Xã" value={form.ward} onChange={set("ward")} />
                    </div>
                    <div className="col-6">
                      <input className="control" id="tbDiaChiNhanHang" type="text" placeholder=" " value={form.street} onChange={set("street")} required />
                      <label className="form-label" htmlFor="tbDiaChiNhanHang">
                        Số nhà, tên đường
                      </label>
                    </div>
                  </div>
                </div>
                <div className="col-12">
                  <div className="row" style={{ height: "100%" }}>
                    <div className="col-12">
                      <textarea id="tbYeuCauKhac" className="control" rows={3} placeholder=" " value={form.note} onChange={set("note")} />
                      <label className="form-label" htmlFor="tbYeuCauKhac">
                        Yêu cầu khác (Không bắt buộc)
                      </label>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div className="modal-footer">
            <button type="submit" className="confirm-btn">
              Xác Nhận
            </button>
          </div>
        </form>
      </div>
    </>
  );
}
