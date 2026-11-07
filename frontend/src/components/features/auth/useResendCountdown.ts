"use client";

import { useCallback, useEffect, useState } from "react";

/** Giãn cách gửi lại mã của server (app.otp.resend-interval = 60 giây, BR-11). */
export const RESEND_SECONDS = 60;
/** Server báo gửi quá sớm mà ta không biết còn bao lâu → chờ ngắn rồi cho thử lại. */
export const RESEND_RETRY_SECONDS = 10;

/**
 * Đếm ngược cho nút "Gửi lại mã". Bắt đầu ngay khi vào trang (mã vừa được gửi); tải lại trang thì đếm lại từ đầu —
 * gửi sớm sẽ bị server từ chối (OTP_RESEND_TOO_SOON) và form báo lại. `restart(seconds)` đặt lại thời gian chờ.
 */
export function useResendCountdown(initialSeconds: number = RESEND_SECONDS) {
  const [resendAt, setResendAt] = useState(() => Date.now() + initialSeconds * 1000);
  const [now, setNow] = useState(() => Date.now());

  const secondsLeft = Math.max(0, Math.ceil((resendAt - now) / 1000));
  useEffect(() => {
    if (secondsLeft <= 0) return;
    const timer = window.setInterval(() => setNow(Date.now()), 500);
    return () => window.clearInterval(timer);
  }, [secondsLeft]);

  const restart = useCallback((seconds: number = RESEND_SECONDS) => {
    const current = Date.now();
    setResendAt(current + seconds * 1000);
    setNow(current);
  }, []);

  return { secondsLeft, restart };
}
