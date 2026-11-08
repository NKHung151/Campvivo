"use client";
/* eslint-disable @next/next/no-img-element */

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import type { ProductFull, PdpStatic } from "@/types/product";
import { vnd } from "@/lib/format";
import { useShop } from "@/components/features/shop/ShopProvider";
import { fmtRating } from "@/components/features/review/Stars";

/** Thời gian còn lại tới hết chiến dịch: [ngày, giờ, phút, giây]; đã hết → toàn 0 (tải lại trang sẽ thấy giá gốc). */
function useCountdown(iso: string | null) {
  const [left, setLeft] = useState<[number, string, string, string] | null>(null);
  useEffect(() => {
    if (!iso) return;
    const end = new Date(iso).getTime();
    const tick = () => {
      const s = Math.max(0, Math.floor((end - Date.now()) / 1000));
      const pad = (n: number) => String(n).padStart(2, "0");
      setLeft([Math.floor(s / 86_400), pad(Math.floor(s / 3600) % 24), pad(Math.floor((s % 3600) / 60)), pad(s % 60)]);
    };
    tick();
    const t = setInterval(tick, 1000);
    return () => clearInterval(t);
  }, [iso]);
  return left;
}

export function ProductInfo({ p, st }: { p: ProductFull; st: PdpStatic }) {
  const { addToCart, wishlist, toggleWishlist, notify, markViewed } = useShop();
  const router = useRouter();
  // Biến thể (D-37): chọn lần lượt từng tuỳ chọn (vd. Màu sắc rồi Size); tuỳ chọn chỉ 1 giá trị thì chọn sẵn
  const hasOptions = p.options.length > 0;
  const [picked, setPicked] = useState<(string | null)[]>(() => p.options.map((o) => (o.values.length === 1 ? o.values[0] : null)));
  const [qty, setQty] = useState(1);
  const [warn, setWarn] = useState(false);
  const [popup, setPopup] = useState<"voucher" | "policy" | null>(null);
  const left = useCountdown(p.saleEnds);
  /** Biến thể khớp với các giá trị đã chọn ở những tuỳ chọn khác (bỏ qua tuỳ chọn `skip`). */
  const matchesOthers = (v: ProductFull["variants"][number], skip: number) =>
    v.values.every((x, j) => j === skip || !picked[j] || x === picked[j]);
  /** Giá trị có tổ hợp nào đang bán / còn hàng với các lựa chọn hiện tại không. */
  const exists = (i: number, value: string) => p.variants.some((v) => v.values[i] === value && matchesOthers(v, i));
  const inStock = (i: number, value: string) => p.variants.some((v) => v.values[i] === value && v.inv > 0 && matchesOthers(v, i));
  const chosen = hasOptions && picked.every(Boolean) ? (p.variants.find((v) => v.values.every((x, i) => x === picked[i])) ?? null) : null;
  const missing = p.options.filter((_, i) => !picked[i]).map((o) => o.name.toLowerCase());
  const allSoldOut = hasOptions ? !p.variants.some((v) => v.inv > 0) : p.maxQty === 0;
  const fav = wishlist.includes(p.slug);
  const max = Math.max(1, chosen ? chosen.inv : p.maxQty || 1000);
  const pick = (i: number, value: string) => {
    setPicked((prev) => prev.map((x, j) => (j === i ? (x === value ? null : value) : x)));
    setWarn(false);
    setQty(1);
  };

  useEffect(() => {
    markViewed(p.slug);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [p.slug]);

  const [adding, setAdding] = useState(false);
  const add = async (go: boolean) => {
    if (hasOptions && !chosen) {
      setWarn(true);
      return false;
    }
    if (!p.price || adding) return false;
    if (chosen && chosen.inv <= 0) {
      notify(`${chosen.values.join(" / ")} đã hết hàng`);
      return false;
    }
    // TODO(Người 1): giỏ hàng chưa lưu biến thể (cart_items.variant_id) — gửi thêm chosen.id khi API giỏ hàng hỗ trợ (D-37)
    // Giỏ hàng ở server (FR-09): lỗi hết hàng/ngừng bán/chưa đăng nhập đã được ShopProvider báo
    setAdding(true);
    const ok = await addToCart({ productId: p.id }, qty);
    setAdding(false);
    if (!ok) return false;
    if (go) router.push("/cart");
    else notify("Đã thêm sản phẩm vào giỏ hàng");
    return true;
  };

  return (
    <div className="in4mation">
      <a
        className="iconpp2 icon-promotion"
        href="#"
        onClick={(e) => {
          e.preventDefault();
          setPopup("voucher");
        }}
      >
        <img alt="Banner tặng voucher cho khách hàng mới" className="iconpp" src="/assets/pic/products/31e145cd-17ab-4727-80fc-6e809833fe9c.jpg" />
      </a>
      {popup === "voucher" && (
        <div className="lightPopupProperty" style={{ width: 400, display: "block", left: "calc(50% - 200px)" }}>
          <a className="btClosePopup" onClick={() => setPopup(null)}>
            &nbsp;
          </a>
          <div className="popup-promo">
            <h3>
              NHẬN NGAY MÃ GIẢM ĐẾN <span>50K</span>
            </h3>
            <div className="popup-promo-desc">(Áp dụng với đơn hàng tối thiểu 300.000đ)</div>
            <form
              className="form-group"
              onSubmit={(e) => {
                e.preventDefault();
                notify("Mã giảm giá demo: CAMPVIVO50 (không có giá trị thực)");
                setPopup(null);
              }}
            >
              <div className="box-gender-code">
                <input id="rbNam" type="radio" name="gender" defaultChecked /> <label htmlFor="rbNam">Anh</label> <input id="rbNu" type="radio" name="gender" />{" "}
                <label htmlFor="rbNu">Chị</label>
              </div>
              <input type="text" placeholder="Họ và tên*" className="input-name-code" required />
              <input type="tel" placeholder="Số điện thoại*" className="input-phone-code" required />
              <button className="btn-get-code" type="submit">
                NHẬN MÃ
              </button>
            </form>
            <ul className="note">
              <li>Áp dụng cho khách chưa mua hàng tại chuỗi Campvivo.</li>
            </ul>
          </div>
        </div>
      )}
      <div className="khoi1">
        <div className="po-brand-row">
          {p.brand && (
            <div className="brand">
              {p.brandHref ? (
                <Link href={p.brandHref} title={p.brand}>
                  {p.brand}{" "}
                </Link>
              ) : (
                <span>{p.brand}</span>
              )}
            </div>
          )}
        </div>
        <h1 className="tensp">{p.name}</h1>
        <div className="flbox">
          <div className="khungSoSao">
            <div className="core" role="img" aria-label={p.reviewCount ? `${fmtRating(p.rating / 20)} trên 5 sao` : "Chưa có đánh giá"}>
              <div className="stars" style={{ width: `${p.reviewCount ? p.rating : 0}%` }} />
            </div>
            <a href="#tabcontent4">
              <span className="splt" />
              {p.reviewCount ? `Xem ${p.reviewCount} đánh giá` : "Chưa có đánh giá"}
            </a>
            {p.sold && (
              <>
                <span className="splt" />
                Đã bán&nbsp;{p.sold}
              </>
            )}
          </div>
          {p.sku && (
            <div id="pro_sku" className="fr">
              Mã hàng: <b>{p.sku}</b>
            </div>
          )}
        </div>
        {p.short && <div className="des">{p.short}</div>}
        {p.stockUrgency && <div className="stock-urgency stock-low">{p.stockUrgency}</div>}
        <div className="pro-price">
          {p.saleEnds && p.oldPrice ? (
            <div className="promotion-price">
              <div className="promotion-price-left">
                <div className="promotion-desc">
                  Bạn đã tiết kiệm được <b>{p.savePct}</b>
                  {p.campaign && (
                    <Link className="ctkm-campaign-tag" href={`/promotions/${p.campaign.slug}`} prefetch={false}>
                      {p.campaign.name}
                    </Link>
                  )}
                </div>
                <div className="promotion-detail">
                  <div className="promotion-desc">
                    <b className="promotion-special">{vnd(p.price)}</b> (Đã bao gồm VAT)
                  </div>
                  <del>{vnd(p.oldPrice)}</del>
                </div>
              </div>
              <div className="promotion-price-right">
                <div className="promotion-desc">Kết thúc sau</div>
                <div className="promotion-countdown">
                  {left && left[0] > 0 && (
                    <>
                      <div className="block">
                        <span className="remain-days">{left[0]}</span>ngày
                      </div>
                      :
                    </>
                  )}
                  <div className="block">
                    <span className="remain-hours">{left?.[1]}</span>giờ
                  </div>
                  :
                  <div className="block">
                    <span className="remain-minutes">{left?.[2]}</span>phút
                  </div>
                  :
                  <div className="block">
                    <span className="remain-seconds">{left?.[3]}</span>giây
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <div className="price">
              Giá bán: <b>{vnd(p.price)}</b>
              {p.oldPrice ? <del style={{ marginLeft: 8, color: "#999", fontSize: 14 }}>{vnd(p.oldPrice)}</del> : null}(Đã bao gồm VAT)
            </div>
          )}
        </div>
        <div className="pdp-benefits" dangerouslySetInnerHTML={{ __html: st.benefitsHtml }} />
        {p.warranty && (
          <div>
            <div className="cot1">Bảo hành: </div>
            <div className="cot2">{p.warranty}</div>
            <div className="cb h10" />
          </div>
        )}
        <div className="cb" />
      </div>
      <div
        className="khungThuocTinh"
        onClick={(e) => {
          const t = e.target as HTMLElement;
          if (t.closest("a.iconpp2")) {
            e.preventDefault();
            setPopup("policy");
          }
          if (t.closest("a.btClosePopup")) setPopup(null);
        }}
      >
        <div
          dangerouslySetInnerHTML={{
            __html: popup === "policy" ? st.policyHtml.replace('class="lightPopupProperty"', 'class="lightPopupProperty" style="display:block;left:calc(50% - 400px)"') : st.policyHtml,
          }}
          style={{ display: "contents" }}
        />
      </div>
      <div className="wrap-product-option">
        <form id="DatHang" className="bgtool" style={{ position: "relative", zIndex: 1 }} onSubmit={(e) => e.preventDefault()}>
          {p.options.map((o, i) => (
            <div key={o.name} className={`variants${warn && !picked[i] ? " warning" : ""}`}>
              <label>
                {o.name}: {picked[i] && <b>{picked[i]}</b>}
              </label>
              <div className="list">
                {o.values.map((value) => {
                  const ok = exists(i, value);
                  return (
                    <button
                      type="button"
                      key={value}
                      // Hết hàng: gạch chéo; không có tổ hợp với lựa chọn hiện tại: khoá hẳn
                      className={`${picked[i] === value ? "active" : ""}${ok && !inStock(i, value) ? " soldout" : ""}${ok ? "" : " soldout cv-unavail"}`}
                      aria-pressed={picked[i] === value}
                      onClick={() => pick(i, value)}
                    >
                      {value}
                    </button>
                  );
                })}
              </div>
            </div>
          ))}
          {chosen && <div className="cv-var-note">{chosen.inv > 0 ? `Còn ${chosen.inv} sản phẩm` : "Hết hàng — chọn màu / size khác"}</div>}
          <div className={`wrn${warn ? " show" : ""}`}>{warn ? `Vui lòng chọn ${missing.join(", ")}` : ""}</div>
          <div>
            <div className="propertyname">Số lượng:</div>
            <div className="bgtextbox">
              <div className="pdp-qty-stepper">
                <button type="button" className="qty-btn qty-minus" aria-label="Giảm" onClick={() => setQty((q) => Math.max(1, q - 1))}>
                  −
                </button>
                <input
                  id="tbSoLuong"
                  type="number"
                  className="textbox qty-input"
                  min={1}
                  max={max}
                  value={qty}
                  onChange={(e) => setQty(Math.max(1, Math.min(max, +e.target.value || 1)))}
                />
                <button type="button" className="qty-btn qty-plus" aria-label="Tăng" onClick={() => setQty((q) => Math.min(max, q + 1))}>
                  +
                </button>
              </div>
            </div>
          </div>
          {p.viewsToday && (
            <div className="count_today">
              <b>{p.viewsToday}</b> người đã xem hôm nay
            </div>
          )}
          <div className="groupButton">
            {allSoldOut ? (
              <button type="button" className="btBuy1" disabled>
                Hết hàng
              </button>
            ) : (
              <>
                <button type="button" className="btBuy1 gg_add_to_cart" disabled={adding} onClick={() => void add(true)}>
                  Mua ngay
                </button>
                <button type="button" className="btBuy2 gg_add_to_cart" disabled={adding} onClick={() => void add(false)}>
                  Thêm vào giỏ
                </button>
              </>
            )}
          </div>
          <div className="cb h15" />
          <a
            className="addtofav"
            href="#"
            onClick={(e) => {
              e.preventDefault();
              toggleWishlist(p.slug);
              notify(fav ? "Đã bỏ khỏi danh sách yêu thích" : "Đã thêm vào danh sách yêu thích");
            }}
          >
            {fav ? "Đã thêm vào Danh sách yêu thích" : "Thêm vào Danh sách yêu thích"}
          </a>
          <div className="box-check-store">
            <Link href="/pages/he-thong-cua-hang" className="check-store">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
                <path d="M3 9l1-5h16l1 5M4 9v11h16V9M9 20v-6h6v6" />
              </svg>
              Xem cửa hàng có hàng
            </Link>
          </div>
        </form>
      </div>
    </div>
  );
}
