"use client";

import { useState } from "react";
import { SmartLink } from "@/components/ui/SmartLink";
import { ChatIcon, PhoneIcon } from "@/components/ui/icons";
import { useShop } from "@/components/features/shop/ShopProvider";
import { DEAD } from "@/lib/dead-link";

const MENUS = [
  {
    head: { name: "Về CAMPVIVO", href: "/pages/gioi-thieu-ve-campvivo-vn" },
    links: [
      { name: "Giới thiệu", href: "/pages/gioi-thieu-ve-campvivo-vn" },
      { name: "Điều khoản và điều kiện", href: "/pages/dieu-khoan-va-quy-dinh-chung" },
      { name: "Bảo mật thông tin", href: "/pages/bao-mat-thong-tin" },
      { name: "Hệ thống cửa hàng", href: "/pages/he-thong-cua-hang" },
      { name: "Outdoor Journal", href: "/journal" },
      { name: "CSKH 1900 0000 (demo)", href: DEAD },
    ],
  },
  {
    head: { name: "Hỗ trợ", href: DEAD },
    links: [
      { name: "Hướng dẫn đặt hàng", href: "/pages/dat-hang-tai-campvivo-vn" },
      { name: "Khách hàng thân thiết", href: DEAD },
      { name: "Chính sách đổi trả hàng", href: "/pages/chinh-sach-doi-tra-hang-tai-campvivo-vn" },
      { name: "Giao và nhận hàng", href: "/pages/giao-va-nhan-hang-campvivo-vn" },
      { name: "Chính sách thanh toán", href: "/pages/chinh-sach-thanh-toan" },
      { name: "Kinh nghiệm outdoor ", href: "/guides" },
      { name: "Câu hỏi thường gặp", href: "/pages/cac-cau-hoi-thuong-gap-tai-campvivo-vn" },
    ],
  },
  {
    head: { name: "Liên kết hợp tác", href: DEAD },
    links: [
      { name: "Liên kết với Campvivo", href: DEAD },
      { name: "Đối tác quan trọng", href: DEAD },
      { name: "Liên hệ quảng cáo", href: DEAD },
      { name: "Bán hàng doanh nghiệp", href: "/pages/ban-hang-doanh-nghiep-tai-campvivo-vn" },
      { name: "Thông tin tuyển dụng", href: "/pages/co-hoi-viec-lam-tai-campvivo-vn" },
    ],
  },
];

const SOCIAL = [
  { title: "Facebook", href: DEAD, icon: "icon-facebook.svg" },
  { title: "TikTok", href: DEAD, icon: "icon-tiktok.svg" },
  { title: "LinkedIn", href: DEAD, icon: "icon-linkedin.svg" },
  { title: "X Social Networks", href: DEAD, icon: "icon-twitter.svg" },
  { title: "Youtube channel", href: DEAD, icon: "icon-youtube.svg" },
  { title: "Instagram", href: DEAD, icon: "icon-instagram.svg" },
];

export function Footer() {
  const { notify } = useShop();
  const [email, setEmail] = useState("");
  return (
    <footer id="wrapper">
      <div className="container">
        <div className="block1">
          <div id="CommonBottomMenu">
            {MENUS.map((m) => (
              <div className="menu" key={m.head.name}>
                <SmartLink className="item" title={m.head.name} href={m.head.href}>
                  {m.head.name}
                </SmartLink>
                {m.links.map((l) => (
                  <SmartLink className="subItem" title={l.name} href={l.href} key={l.name}>
                    {l.name}
                  </SmartLink>
                ))}
              </div>
            ))}
            <div className="menu">
              <div className="item">NHẬN BẢN TIN SỐNG CÁ TÍNH</div>
              <form
                id="CommonNewsLetterSignUp"
                onSubmit={(e) => {
                  e.preventDefault();
                  notify("Đăng ký nhận bản tin thành công (bản demo – không gửi email).");
                  setEmail("");
                }}
              >
                <input
                  id="txtEmail_LetterSignUp"
                  type="email"
                  className="TBX1"
                  placeholder="Email của bạn"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />{" "}
                <button type="submit" className="LBT1">
                  Gửi
                </button>
              </form>
              <p>CAMPVIVO cam kết bảo mật thông tin khách hàng cung cấp.</p>
            </div>
          </div>
        </div>
        <div className="block2">
          <div className="column">
            <p>
              CSKH: <a title="Nhấn để gửi email" href={DEAD} data-dead="">chamsockhachhang@campvivo.vn</a>
            </p>
            <p>
              Nhà cung cấp: <a title="Nhấn để gửi email" href={DEAD} data-dead="">purchasing@campvivo.vn</a>
            </p>
            <p>
              Khách doanh nghiệp: <a title="Nhấn để gửi email" href={DEAD} data-dead="">b2b@campvivo.vn</a>
            </p>
          </div>
          <div className="column">
            <label>
              <ChatIcon /> Live chat
            </label>
            <p>
              <a rel="nofollow noreferrer" href={DEAD} data-dead="">
                Zalo Campvivo
              </a>
            </p>
            <p>
              <a rel="nofollow noreferrer" href={DEAD} data-dead="">
                Messenger
              </a>
            </p>
          </div>
          <div className="column">
            <label>
              <PhoneIcon />
              Gọi miễn phí
            </label>
            <p>
              <a title="Gọi Campvivo" rel="nofollow" href={DEAD} data-dead="">
                1900 0000 (demo)
              </a>
            </p>
            <p>9.30AM - 9.30PM</p>
          </div>
        </div>
        <div className="block3">
          <div className="social">
            {SOCIAL.map((s) => (
              <a key={s.title} title={s.title} href={DEAD} data-dead="" rel="nofollow noreferrer">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={`/assets/css/icon/${s.icon}`} width={28} height={28} loading="lazy" alt={s.title} />
              </a>
            ))}
          </div>
          <p className="copyright">
            Copyright © 2026 CAMPVIVO. All rights reserved.
          </p>
          {/* TODO: thông tin pháp lý thật của Campvivo (tên công ty, địa chỉ, ĐKKD) khi có. */}
          <p className="company">Campvivo — Đồ án tốt nghiệp (website demo)</p>
          <p className="desc">Thông tin doanh nghiệp, địa chỉ và số ĐKKD sẽ cập nhật khi có.</p>
        </div>
      </div>
    </footer>
  );
}
