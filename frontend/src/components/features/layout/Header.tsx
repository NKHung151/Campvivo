"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import type { MenuItem } from "@/types/shop";
import { SmartLink } from "@/components/ui/SmartLink";
import { useShop } from "@/components/features/shop/ShopProvider";
import { CartIcon, PhoneIcon, PinIcon, SearchIcon, UserIcon } from "@/components/ui/icons";
import { DEAD } from "@/lib/dead-link";

const TOP_LINKS = [
  { name: "New arrivals", href: "/categories/san-pham-moi" },
  { name: "Campvivology", href: "/guides" },
  { name: "Outdoor Journal", href: "/journal" },
  { name: "WeExperience", href: DEAD },
  { name: "WePoints", href: DEAD },
  { name: "Why Campvivo", href: DEAD },
  { name: "Tuyển dụng", href: DEAD },
];

export function Header({ menu, initialQuery = "" }: { menu: MenuItem[]; initialQuery?: string }) {
  const { cart, user, ready, openLogin } = useShop();
  const router = useRouter();
  const [q, setQ] = useState(initialQuery);
  const [popOpen, setPopOpen] = useState(false);
  const count = cart.reduce((n, c) => n + c.qty, 0);

  // campvivo.vn opens the "ĐĂNG NHẬP để có trải nghiệm tốt nhất" popover on each visit until dismissed.
  useEffect(() => {
    if (!ready || user) return;
    try {
      if (!window.sessionStorage.getItem("cv-signin-pop")) setPopOpen(true);
    } catch {
      setPopOpen(true);
    }
  }, [ready, user]);

  const closePop = () => {
    setPopOpen(false);
    try {
      window.sessionStorage.setItem("cv-signin-pop", "1");
    } catch {
      /* ignore */
    }
  };

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const key = q.trim();
    if (!key) return;
    router.push(`/search?q=${encodeURIComponent(key)}`);
  };

  return (
    <header>
      <section className="menuTop">
        <div className="container">
          <div className="nav_primary">
            {TOP_LINKS.map((l) => (
              <SmartLink key={l.name} className="nm" title={l.name} href={l.href}>
                {l.name}{" "}
              </SmartLink>
            ))}
          </div>
          <div className="hotline">
            <PhoneIcon /> Gọi đặt hàng (miễn phí): <a href={DEAD} data-dead=""> 1900 0000</a> (9.30 - 21.30){" "}
          </div>
        </div>
      </section>
      <section className="masthead">
        <div className="container">
          <Link className="logo" href="/">
            <span className="cv-logo">Campvivo</span>
          </Link>
          <form id="searchBox" className="searchBox" onSubmit={submit}>
            <input
              type="text"
              id="s"
              name="key"
              placeholder="Bạn chuẩn bị đi đâu?"
              autoComplete="off"
              value={q}
              onChange={(e) => setQ(e.target.value)}
            />
            <button type="submit" title="search">
              <SearchIcon />
            </button>
          </form>
          <div className="actions">
            <Link className="action" href="/pages/he-thong-cua-hang">
              <PinIcon /> Campvivo Stores
            </Link>
            <div className="box-member-header">
              <div className="cv-signin-wrap">
                {user ? (
                  <Link className="action" href="/account">
                    <UserIcon /> {user.name}
                  </Link>
                ) : (
                  <a
                    className="action"
                    href="#"
                    onClick={(e) => {
                      e.preventDefault();
                      closePop();
                      openLogin();
                    }}
                  >
                    <UserIcon /> Đăng nhập
                  </a>
                )}
                <div className={`cv-signin-pop${popOpen && !user ? " open" : ""}`} id="wtSigninPop" style={{ top: 123, right: "calc(50% - 466px)" }}>
                  <button type="button" className="cv-pop-x" onClick={closePop} aria-label="Đóng">
                    ×
                  </button>
                  <div className="cv-pop-title">
                    <b>ĐĂNG NHẬP</b> để có trải nghiệm tốt nhất
                  </div>
                  <ul className="cv-pop-list">
                    <li>Thanh toán nhanh hơn</li>
                    <li>Theo dõi đơn hàng dễ dàng</li>
                    <li>Tích &amp; dùng điểm WePoint, giảm 5% từ đơn thứ 2</li>
                  </ul>
                  {(["Tạo tài khoản", "Đăng nhập"] as const).map((t, i) => (
                    <a
                      key={t}
                      className={`cv-pop-btn ${i === 0 ? "cv-pop-primary" : "cv-pop-second"}`}
                      href="#"
                      onClick={(e) => {
                        e.preventDefault();
                        closePop();
                        openLogin();
                      }}
                    >
                      {t}
                    </a>
                  ))}
                </div>
              </div>
            </div>
            <Link className="action" href="/cart">
              <CartIcon /> Giỏ hàng (<span className="CountItemsInCart">{count}</span>)
            </Link>
          </div>
        </div>
      </section>
      <section className="menuMain">
        <ul className="container">
          {menu.map((m) => (
            <li key={m.name}>
              <SmartLink className={`item${m.special ? " special" : ""}`} title={m.name} href={m.href}>
                <span className="center">{m.name} </span>
              </SmartLink>
              {m.groups.length > 0 && (
                <div className="submenumain">
                  <div className="leftmenu">
                    {m.groups.map((g) => (
                      <div className="cate" key={g.href + g.name}>
                        <div className="catePic">
                          {g.img && (
                            // eslint-disable-next-line @next/next/no-img-element
                            <img loading="lazy" alt={g.name} width={70} src={g.img} />
                          )}
                        </div>
                        <div className="cateName">
                          <SmartLink className="subcate" title={g.name} href={g.href}>
                            {g.name}
                          </SmartLink>
                          {(g.links.length > 0 || g.more.length > 0) && (
                            <div className="head">
                              {g.links.map((l) => (
                                <SmartLink key={l.href + l.name} className="subcate2" title={l.name} href={l.href}>
                                  {l.name}
                                </SmartLink>
                              ))}
                              {g.more.length > 0 && (
                                <div className="more">
                                  <span className="subcate2 moreBt">More...</span>
                                  <div className="moreHover">
                                    {g.more.map((l) => (
                                      <SmartLink key={l.href + l.name} className="subcate2" title={l.name} href={l.href}>
                                        {l.name}
                                      </SmartLink>
                                    ))}
                                  </div>
                                </div>
                              )}
                            </div>
                          )}
                        </div>
                        <div className="cb" />
                      </div>
                    ))}
                  </div>
                  {m.rightHtml && <div className="rightmenu" dangerouslySetInnerHTML={{ __html: m.rightHtml }} />}
                </div>
              )}
            </li>
          ))}
        </ul>
      </section>
    </header>
  );
}
