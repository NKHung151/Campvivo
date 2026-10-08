"use client";

import { useState } from "react";
import { useShop } from "@/components/features/shop/ShopProvider";

/** Phone → 4-digit OTP flow from campvivo.vn, mocked locally (any 4 digits pass). */
export function LoginPopup() {
  const { loginOpen, closeLogin, login, notify } = useShop();
  const [step, setStep] = useState<1 | 2>(1);
  const [phone, setPhone] = useState("");
  const [otp, setOtp] = useState("");

  if (!loginOpen) return null;

  const close = () => {
    closeLogin();
    setStep(1);
    setOtp("");
  };

  const finish = (p: string, name?: string) => {
    login(p, name);
    notify("Đăng nhập thành công!");
    close();
  };

  return (
    <div id="lightPopupCommonLogin" style={{ display: "block" }}>
      <div className="wrp">
        <div id="fadePopupCommonLogin" onClick={close} style={{ display: "block" }} />
        <div id="CommonLogin">
          <span className="btClosePopup" onClick={close}>
            x
          </span>
          <div className="loginform">
            <div className="top top1" style={{ display: step === 1 ? undefined : "none" }}>
              <div className="title">Nhập số điện thoại của bạn để tiếp tục</div>
              <form
                id="loginForm"
                className="form-control"
                onSubmit={(e) => {
                  e.preventDefault();
                  setStep(2);
                }}
              >
                <input
                  id="txtPhone"
                  type="tel"
                  pattern="(09|03|07|08|05)[0-9]{8}"
                  placeholder="0912345678"
                  required
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                />
                <button id="btLogin" type="submit" className="btLogin" aria-label="Tiếp tục" />
              </form>
            </div>
            <div className="top top2" style={{ display: step === 2 ? "block" : "none" }}>
              <div className="title">
                Nhập mật khẩu 4 chữ số được gửi đến <b>{phone}</b>
              </div>
              <form
                id="loginForm2"
                className="form-control"
                onSubmit={(e) => {
                  e.preventDefault();
                  if (/^\d{4}$/.test(otp)) finish(phone);
                }}
              >
                <input id="txtOtp" type="tel" required pattern="\d{4}" maxLength={4} value={otp} onChange={(e) => setOtp(e.target.value)} />
                <button type="submit" className="btLogin" aria-label="Xác nhận" />
              </form>
              <p style={{ fontSize: 12, color: "#888", marginTop: 8 }}>Bản demo: nhập 4 chữ số bất kỳ.</p>
            </div>
            <div className="hoac">hoặc</div>
            <div id="CommonLoginOther">
              <a id="login" onClick={() => finish("0900000000", "Facebook User")}>
                Tiếp tục bằng Facebook
              </a>
              <a id="loginGG" onClick={() => finish("0900000001", "Google User")}>
                Tiếp tục bằng Google
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
