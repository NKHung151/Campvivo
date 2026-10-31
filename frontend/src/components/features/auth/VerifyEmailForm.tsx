"use client";

import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { useAuth } from "@/components/features/auth/AuthProvider";
import { useLandingRedirect } from "@/components/features/auth/useLandingRedirect";
import { getErrorMessage } from "@/lib/errorMessages";
import { authHref } from "@/lib/auth/redirect";
import { normalizeOtpInput, OTP_LENGTH, validateEmail, validateOtp } from "@/lib/auth/validation";
import { isApiError } from "@/services/apiError";
import { authService } from "@/services/authService";

/** Giãn cách gửi lại mã của server (app.otp.resend-interval = 60 giây, BR-11). */
const RESEND_SECONDS = 60;
/** Server báo gửi quá sớm mà ta không biết còn bao lâu → chờ ngắn rồi cho thử lại. */
const RESEND_RETRY_SECONDS = 10;

/**
 * Nhập mã OTP xác thực email (POST /auth/verify-email) và gửi lại mã (POST /auth/resend-code) có đếm ngược.
 * Đúng mã → đăng nhập luôn → useLandingRedirect đưa về trang đích. Đếm ngược bắt đầu khi vào trang vì mã vừa được
 * gửi (từ /register hoặc /login); tải lại trang thì đếm lại từ đầu — gửi sớm sẽ bị server từ chối và ta báo lại.
 */
export function VerifyEmailForm({ email, redirect }: { email: string | null; redirect: string | null }) {
  const { status, user, verifyEmail } = useAuth();
  const [code, setCode] = useState("");
  const [codeError, setCodeError] = useState<string | undefined>();
  const [formError, setFormError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [resending, setResending] = useState(false);
  const [resendAt, setResendAt] = useState(() => Date.now() + RESEND_SECONDS * 1000);
  const [now, setNow] = useState(() => Date.now());

  useLandingRedirect(redirect);

  const secondsLeft = Math.max(0, Math.ceil((resendAt - now) / 1000));
  useEffect(() => {
    if (secondsLeft <= 0) return;
    const timer = window.setInterval(() => setNow(Date.now()), 500);
    return () => window.clearInterval(timer);
  }, [secondsLeft]);

  const emailInvalid = email === null || validateEmail(email) !== undefined;

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || email === null) return;

    const error = validateOtp(code);
    setCodeError(error);
    setFormError(null);
    setNotice(null);
    if (error) return;

    setSubmitting(true);
    try {
      await verifyEmail(email, code);
      // Điều hướng do useLandingRedirect thực hiện khi AuthProvider cập nhật user.
    } catch (err) {
      if (isApiError(err) && err.errorCode === "VALIDATION_ERROR") setCodeError(err.fieldErrors.code);
      // Mã sai / hết hạn / quá số lần: xoá ô để nhập lại (hoặc gửi lại mã).
      setCode("");
      setFormError(getErrorMessage(err));
      setSubmitting(false);
    }
  }

  async function onResend() {
    if (resending || secondsLeft > 0 || email === null) return;
    setResending(true);
    setFormError(null);
    setNotice(null);
    setCodeError(undefined);
    try {
      await authService.resendCode({ email, purpose: "VERIFY_EMAIL" });
      setNotice("Đã gửi mã mới. Mã cũ không còn dùng được.");
      setCode("");
      setResendAt(Date.now() + RESEND_SECONDS * 1000);
    } catch (err) {
      setFormError(getErrorMessage(err));
      if (isApiError(err) && err.errorCode === "OTP_RESEND_TOO_SOON") {
        setResendAt(Date.now() + RESEND_RETRY_SECONDS * 1000);
      }
    } finally {
      setNow(Date.now());
      setResending(false);
    }
  }

  if (status === "loading" || (status === "authenticated" && user)) {
    return (
      <div className="cv-auth__status" role="status">
        <span className="cv-ui-spinner" aria-hidden="true" />
        {status === "loading" ? "Đang kiểm tra phiên đăng nhập…" : "Đang chuyển hướng…"}
      </div>
    );
  }

  if (emailInvalid) {
    return (
      <div>
        <h1 className="cv-auth__title">Xác thực email</h1>
        <div className="cv-auth__alert" role="alert">
          Không xác định được email cần xác thực. Vui lòng đăng ký hoặc đăng nhập lại.
        </div>
        <p className="cv-auth__switch">
          <Link href={authHref("/register", {}, redirect)}>Đăng ký</Link> ·{" "}
          <Link href={authHref("/login", {}, redirect)}>Đăng nhập</Link>
        </p>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} noValidate>
      <h1 className="cv-auth__title">Xác thực email</h1>
      <p className="cv-auth__lead">
        Chúng tôi đã gửi mã {OTP_LENGTH} số tới <strong>{email}</strong>. Mã có hiệu lực trong 10 phút.
      </p>

      {formError && (
        <div className="cv-auth__alert" role="alert">
          {formError}
        </div>
      )}
      {notice && (
        <div className="cv-auth__notice" role="status">
          {notice}
        </div>
      )}

      <Input
        label="Mã xác thực"
        name="code"
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={OTP_LENGTH}
        autoFocus
        value={code}
        onChange={(e) => setCode(normalizeOtpInput(e.target.value))}
        error={codeError}
      />

      <Button type="submit" fullWidth loading={submitting}>
        {submitting ? "Đang xác thực…" : "Xác thực"}
      </Button>

      <p className="cv-auth__switch">
        Chưa nhận được mã?{" "}
        <button
          type="button"
          className="cv-auth__link-btn"
          onClick={onResend}
          disabled={secondsLeft > 0 || resending}
        >
          {resending ? "Đang gửi…" : secondsLeft > 0 ? `Gửi lại mã (${secondsLeft}s)` : "Gửi lại mã"}
        </button>
      </p>
      <p className="cv-auth__switch">
        <Link href={authHref("/register", {}, redirect)}>Dùng email khác</Link>
      </p>
    </form>
  );
}
