"use client";

import { useState } from "react";
import type { FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { useAuth } from "@/components/features/auth/AuthProvider";
import { useLandingRedirect } from "@/components/features/auth/useLandingRedirect";
import {
  RESEND_RETRY_SECONDS,
  RESEND_SECONDS,
  useResendCountdown,
} from "@/components/features/auth/useResendCountdown";
import { getErrorMessage } from "@/lib/errorMessages";
import { authHref } from "@/lib/auth/redirect";
import {
  normalizeOtpInput,
  OTP_LENGTH,
  validateEmail,
  validateOtp,
  validatePassword,
} from "@/lib/auth/validation";
import { isApiError } from "@/services/apiError";
import { authService } from "@/services/authService";

interface FieldErrors {
  code?: string;
  newPassword?: string;
}

/**
 * Đặt lại mật khẩu (POST /auth/reset-password, FR-03): mã OTP 6 số + mật khẩu mới; có nút "Gửi lại mã" đếm ngược 60 giây
 * (POST /auth/resend-code, purpose RESET_PASSWORD). Thành công KHÔNG đăng nhập luôn (mọi thiết bị đã bị đăng xuất):
 * chuyển /login?reset=1 kèm thông báo. Câu mô tả giữ trung tính ("nếu email tồn tại...") vì server không tiết lộ email có thật hay không.
 */
export function ResetPasswordForm({ email, redirect }: { email: string | null; redirect: string | null }) {
  const router = useRouter();
  const { status, user } = useAuth();
  const [code, setCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [resending, setResending] = useState(false);
  const { secondsLeft, restart } = useResendCountdown();

  useLandingRedirect(redirect);

  const emailInvalid = email === null || validateEmail(email) !== undefined;

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || email === null) return;

    const errors: FieldErrors = { code: validateOtp(code), newPassword: validatePassword(newPassword) };
    setFieldErrors(errors);
    setFormError(null);
    setNotice(null);
    if (errors.code || errors.newPassword) return;

    setSubmitting(true);
    try {
      await authService.resetPassword({ email, code, newPassword });
      router.push(authHref("/login", { reset: "1" }, redirect));
    } catch (err) {
      if (isApiError(err) && err.errorCode === "VALIDATION_ERROR") {
        setFieldErrors({ code: err.fieldErrors.code, newPassword: err.fieldErrors.newPassword });
      } else {
        // Mã sai / hết hạn / quá số lần: xoá ô mã để nhập lại (hoặc gửi lại mã). Giữ mật khẩu đã gõ.
        setCode("");
      }
      setFormError(getErrorMessage(err));
      setSubmitting(false);
    }
  }

  async function onResend() {
    if (resending || secondsLeft > 0 || email === null) return;
    setResending(true);
    setFormError(null);
    setNotice(null);
    setFieldErrors({});
    try {
      await authService.resendCode({ email, purpose: "RESET_PASSWORD" });
      setNotice("Nếu email tồn tại, mã mới đã được gửi. Mã cũ không còn dùng được.");
      setCode("");
      restart(RESEND_SECONDS);
    } catch (err) {
      setFormError(getErrorMessage(err));
      if (isApiError(err) && err.errorCode === "OTP_RESEND_TOO_SOON") restart(RESEND_RETRY_SECONDS);
    } finally {
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
        <h1 className="cv-auth__title">Đặt lại mật khẩu</h1>
        <div className="cv-auth__alert" role="alert">
          Không xác định được email cần đặt lại mật khẩu. Vui lòng nhập lại email.
        </div>
        <p className="cv-auth__switch">
          <Link href={authHref("/forgot-password", {}, redirect)}>Quên mật khẩu</Link> ·{" "}
          <Link href={authHref("/login", {}, redirect)}>Đăng nhập</Link>
        </p>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} noValidate>
      <h1 className="cv-auth__title">Đặt lại mật khẩu</h1>
      <p className="cv-auth__lead">
        Nếu <strong>{email}</strong> đã đăng ký, chúng tôi đã gửi mã {OTP_LENGTH} số tới email này. Mã có hiệu lực trong 10 phút.
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
        error={fieldErrors.code}
      />
      <Input
        label="Mật khẩu mới"
        type="password"
        name="newPassword"
        autoComplete="new-password"
        revealable
        value={newPassword}
        onChange={(e) => setNewPassword(e.target.value)}
        error={fieldErrors.newPassword}
        hint="8–64 ký tự, có ít nhất 1 chữ và 1 số."
      />

      <Button type="submit" fullWidth loading={submitting}>
        {submitting ? "Đang đặt lại…" : "Đặt lại mật khẩu"}
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
        <Link href={authHref("/forgot-password", {}, redirect)}>Dùng email khác</Link>
      </p>
    </form>
  );
}
