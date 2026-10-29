"use client";

import { usePathname } from "next/navigation";
import { useState } from "react";
import { useAuth } from "@/components/features/auth/AuthProvider";
import { ChatPanel } from "./ChatPanel";

/**
 * Nút chat nổi góc phải mọi trang storefront (FR-28, pages.md). Chỉ khách hàng (CUSTOMER) chat được; chưa đăng
 * nhập → mời đăng nhập; nhân viên/admin → ẩn. Quyền thật luôn do chatbot-service kiểm.
 */
export function ChatLauncher() {
  const { status, user } = useAuth();
  const pathname = usePathname();
  const [open, setOpen] = useState(false);

  if (status === "loading" || status === "error") return null;
  if (status === "authenticated" && user?.role !== "CUSTOMER") return null;
  // Đang ở trang đăng nhập/đăng ký thì không chen nút chat
  if (pathname.startsWith("/login") || pathname.startsWith("/register")) return null;

  // Quay lại đúng trang đang xem sau khi đăng nhập
  const goLogin = () => {
    const { pathname: path, search } = window.location;
    window.location.href = `/login?redirect=${encodeURIComponent(path + search)}`;
  };

  return (
    <div className={`cv-chat${open ? " is-open" : ""}`}>
      {open &&
        (status === "authenticated" ? (
          <ChatPanel onClose={() => setOpen(false)} />
        ) : (
          <div className="cv-chat-panel cv-chat-guest" role="dialog" aria-label="Trợ lý Campvivo">
            <header className="cv-chat-head">
              <div className="cv-chat-title">
                <strong>Trợ lý Campvivo</strong>
                <span>Tư vấn đồ dã ngoại, combo, chính sách</span>
              </div>
              <button type="button" className="cv-chat-icon-btn" onClick={() => setOpen(false)} aria-label="Đóng">
                ✕
              </button>
            </header>
            <div className="cv-chat-body cv-chat-welcome">
              <p>Đăng nhập để chat với trợ lý: tìm sản phẩm theo tầm giá, tư vấn combo theo số người và ngân sách.</p>
              <button type="button" className="cv-ui-btn cv-ui-btn--primary" onClick={goLogin}>
                Đăng nhập
              </button>
            </div>
          </div>
        ))}
      <button
        type="button"
        className="cv-chat-launcher"
        onClick={() => setOpen((v) => !v)}
        aria-label={open ? "Đóng trợ lý" : "Mở trợ lý Campvivo"}
        aria-expanded={open}
      >
        {open ? "✕" : "💬"}
        {!open && <span>Hỏi trợ lý</span>}
      </button>
    </div>
  );
}
