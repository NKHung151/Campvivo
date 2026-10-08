"use client";
/* eslint-disable @next/next/no-img-element */

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import type { ProductFull, PdpStatic } from "@/types/product";
import { vnd } from "@/lib/format";
import { useShop } from "@/components/features/shop/ShopProvider";

function useCountdown(iso: string | null) {
  const [left, setLeft] = useState<[string, string, string] | null>(null);
  useEffect(() => {
    if (!iso) return;
    const end = new Date(iso).getTime();
    const tick = () => {
      // Sale end dates in the snapshot may already be past; roll forward by days so the timer keeps running.
      let ms = end - Date.now();
      while (ms < 0) ms += 86_400_000;
      const s = Math.floor(ms / 1000);
      const pad = (n: number) => String(n).padStart(2, "0");
      setLeft([pad(Math.floor(s / 3600) % 24), pad(Math.floor((s % 3600) / 60)), pad(s % 60)]);
    };
    tick();
    const t = setInterval(tick, 1000);
    return () => clearInterval(t);
  }, [iso]);
  return left;
}

export function ProductInfo({ p, st }: { p: ProductFull; st: PdpStatic }) {
  const { addToCart, user, openLogin, wishlist, toggleWishlist, notify, markViewed } = useShop();
  const router = useRouter();
  const firstAvail = p.variants.findIndex((v) => v.inv > 0);
  const [variant, setVariant] = useState<number>(p.variants.length === 1 ? 0 : -1);
  const [qty, setQty] = useState(1);
  const [warn, setWarn] = useState(false);
  const [popup, setPopup] = useState<"voucher" | "policy" | null>(null);
  const [memberDes, setMemberDes] = useState(false);
  const [pointDes, setPointDes] = useState(false);
  const [notifyEmail, setNotifyEmail] = useState("");
  const left = useCountdown(p.saleEnds);
  const allSoldOut = p.variants.length > 0 && firstAvail === -1;
  const fav = wishlist.includes(p.slug);
  const max = p.variants[variant]?.inv || p.maxQty || 1000;

  useEffect(() => {
    markViewed(p.slug);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [p.slug]);

  const add = (go: boolean) => {
    if (p.variants.length > 1 && variant < 0) {
      setWarn(true);
      return false;
    }
    if (!p.price) return false;
    addToCart(
      {
        slug: p.slug,
        name: p.name,
        img: p.images[0]?.thumb ?? "",
        sku: p.sku,
        variant: p.variants[variant]?.name ?? null,
        options: p.variants.map((v) => v.name),
        variantLabel: p.variantLabel,
        stock: p.variants[variant]?.inv ?? p.maxQty,
        price: p.price,
      },
      qty,
    );
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
            <div className="core">
              <div className="stars" style={{ width: `${p.rating || 100}%` }} />
            </div>
            <a href="#tabcontent4">
              <span className="splt" />
              Xem {p.reviewCount} đánh giá
            </a>
            {p.sold && (
              <>
                <span className="splt" />
                Đã bán&nbsp;{p.sold}
              </>
            )}
          </div>
          <div id="pro_sku" className="fr">
            Mã hàng: <b>{p.sku}</b>
          </div>
        </div>
        <div className="des">{p.short}</div>
        {p.stockUrgency && <div className="stock-urgency stock-low">{p.stockUrgency}</div>}
        <div className="pro-price">
          {p.saleEnds && p.oldPrice ? (
            <div className="promotion-price">
              <div className="promotion-price-left">
                <div className="promotion-desc">
                  Bạn đã tiết kiệm được <b>{p.savePct}</b>
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
                  <div className="block">
                    <span className="remain-hours">{left?.[0]}</span>giờ
                  </div>
                  :
                  <div className="block">
                    <span className="remain-minutes">{left?.[1]}</span>phút
                  </div>
                  :
                  <div className="block">
                    <span className="remain-seconds">{left?.[2]}</span>giây
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
          {p.memberPrice ? (
            <div className="loyalty-price">
              <span className="lp-main">Giá Member {vnd(p.memberPrice)}</span>
              <span className="member-tier">−5% từ đơn thứ 2</span>
              <div className="ic-question" onClick={() => setMemberDes((v) => !v)} />
              {!user && (
                <a
                  className="lp-login"
                  href="#"
                  onClick={(e) => {
                    e.preventDefault();
                    openLogin();
                  }}
                >
                  Đăng nhập để nhận ưu đãi
                </a>
              )}
              <ul className="member-des" style={memberDes ? { display: "block" } : undefined} dangerouslySetInnerHTML={{ __html: st.memberDesHtml }} />
            </div>
          ) : null}
        </div>
        <div className="pdp-benefits" dangerouslySetInnerHTML={{ __html: st.benefitsHtml }} />
        {p.warranty && (
          <div>
            <div className="cot1">Bảo hành: </div>
            <div className="cot2">{p.warranty}</div>
            <div className="cb h10" />
          </div>
        )}
        {p.discontinued && <div className="eol_product_label">Sản phẩm ngừng kinh doanh</div>}
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
      {!p.discontinued && (
        <div className="wrap-product-option">
          <form id="DatHang" className="bgtool" style={{ position: "relative", zIndex: 1 }} onSubmit={(e) => e.preventDefault()}>
            {p.variants.length > 0 && (
              <div className={`variants${warn ? " warning" : ""}`}>
                <label>{p.variantLabel || "Phân loại"}:</label>
                <div className="list">
                  {p.variants.map((v, i) => (
                    <button
                      type="button"
                      key={v.name + i}
                      className={`${variant === i ? "active" : ""}${v.inv <= 0 ? " soldout" : ""}`}
                      onClick={() => {
                        setVariant(i);
                        setWarn(false);
                        setQty(1);
                      }}
                    >
                      {v.name}
                    </button>
                  ))}
                </div>
              </div>
            )}
            <div className={`wrn${warn ? " show" : ""}`}>{warn ? `Vui lòng chọn ${(p.variantLabel || "phân loại").toLowerCase()}` : ""}</div>
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
            {p.wepoint && (
              <div className="loyalty__main__point">
                <div className="point-text">
                  {" "}
                  + <b>{p.wepoint} </b> điểm tích lũy WePoint <div className="ic-question" onClick={() => setPointDes((v) => !v)} />
                </div>
                <ul className={`point-des${pointDes ? " show" : ""}`} dangerouslySetInnerHTML={{ __html: st.pointDesHtml }} />
              </div>
            )}
            <div className="groupButton">
              {allSoldOut || p.preOrder ? (
                <>
                  <button type="button" className="btBuy1 gg_pre_order" onClick={() => add(true)}>
                    ĐẶT HÀNG
                  </button>
                  <div className="wdc-noti" style={{ marginTop: 10, padding: "12px 14px", border: "1px solid #dcdfe3", borderRadius: 8, background: "#fafbfc" }}>
                    <div style={{ fontSize: 13, color: "#1c1e21", marginBottom: 8 }}>
                      Sản phẩm đang hết hàng. Để lại email, Campvivo báo bạn ngay khi có hàng trở lại.
                    </div>
                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                      <input
                        type="email"
                        placeholder="Email của bạn"
                        value={notifyEmail}
                        onChange={(e) => setNotifyEmail(e.target.value)}
                        style={{ flex: "1 1 200px", minWidth: 180, padding: "8px 10px", border: "1px solid #ccd0d5", borderRadius: 6, fontSize: 13 }}
                      />
                      <button
                        type="button"
                        onClick={() => {
                          if (!/\S+@\S+/.test(notifyEmail)) return;
                          notify("Campvivo sẽ báo bạn khi có hàng (bản demo).");
                          setNotifyEmail("");
                        }}
                        style={{ padding: "8px 14px", border: 0, borderRadius: 6, background: "#2f6a3f", color: "#fff", fontSize: 13, cursor: "pointer" }}
                      >
                        Báo khi có hàng
                      </button>
                    </div>
                  </div>
                </>
              ) : (
                <>
                  <button type="button" className="btBuy1 gg_add_to_cart" onClick={() => add(true)}>
                    Mua ngay
                  </button>
                  <button type="button" className="btBuy2 gg_add_to_cart" onClick={() => add(false)}>
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
      )}
    </div>
  );
}
