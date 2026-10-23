"use client";

import { useEffect, useState } from "react";
import { useShop } from "@/components/features/shop/ShopProvider";

interface LocalComment {
  name: string;
  date: string;
  text: string;
}

/**
 * "bình luận danh mục" block. campvivo.vn loads existing comments via AJAX (not captured);
 * here the form works locally and stores comments in this browser only.
 */
export function CategoryComments({ storageKey }: { storageKey: string }) {
  const { notify } = useShop();
  const key = `cv-comments:${storageKey}`;
  const [list, setList] = useState<LocalComment[]>([]);
  const [form, setForm] = useState({ name: "", email: "", phone: "", text: "", captcha: "" });

  useEffect(() => {
    try {
      setList(JSON.parse(window.localStorage.getItem(key) ?? "[]") as LocalComment[]);
    } catch {
      /* ignore */
    }
  }, [key]);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const next = [{ name: form.name, text: form.text, date: new Date().toLocaleString("vi-VN") }, ...list];
    setList(next);
    try {
      window.localStorage.setItem(key, JSON.stringify(next));
    } catch {
      /* ignore */
    }
    setForm({ name: "", email: "", phone: "", text: "", captcha: "" });
    notify("Cảm ơn bạn đã gửi bình luận!");
  };

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => setForm((f) => ({ ...f, [k]: e.target.value }));

  return (
    <div id="DisplaySubProductCommentCategoryLarge">
      <div className="titleComment">
        <span className="soLuongBL">{list.length || ""}</span> bình luận danh mục <span className="tensp" />
      </div>
      <div id="ListComment">
        <div className="cb h10" />
        <div className="h10" />
        <form id="form_comment" onSubmit={submit}>
          <div className="leftKhung">
            <input type="text" className="txtNormal" placeholder="Họ tên *" required value={form.name} onChange={set("name")} />
            <div className="h10" />
            <input type="email" className="txtNormal" placeholder="Email *" required value={form.email} onChange={set("email")} />
            <div className="h10" />
            <input type="tel" className="txtNormal" placeholder="Số điện thoại (không bắt buộc)" value={form.phone} onChange={set("phone")} />
          </div>
          <div className="rightKhung">
            <div>
              <textarea className="txtBig" placeholder="Nội dung bình luận *" required value={form.text} onChange={set("text")} />
              <div className="cb" />
              <div style={{ float: "left" }}>
                <input type="text" className="textBoxMaBaoVe" placeholder="Mã bảo vệ *" required value={form.captcha} onChange={set("captcha")} />
              </div>
              <div className="fl w86" style={{ overflow: "hidden", height: 26, marginLeft: 6, fontFamily: "monospace", letterSpacing: 3, lineHeight: "26px", background: "#ddd", textAlign: "center" }}>
                WETRK
              </div>
              <button type="submit" id="gui" className="btn">
                Gửi đi
              </button>
              <div className="cb" />
            </div>
          </div>
        </form>
        <div className="cb h20" />
        {list.map((c, i) => (
          <div className="comment" key={i} style={{ borderBottom: "1px solid #eee", padding: "10px 0" }}>
            <b>{c.name}</b> <span style={{ color: "#999", fontSize: 12 }}>- {c.date}</span>
            <div style={{ marginTop: 4 }}>{c.text}</div>
          </div>
        ))}
        <div className="cb h30" />
      </div>
    </div>
  );
}
