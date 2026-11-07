"use client";

import { useState } from "react";
import type { FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { useAuth } from "@/components/features/auth/AuthProvider";
import { useLandingRedirect } from "@/components/features/auth/useLandingRedirect";
import { getErrorMessage } from "@/lib/errorMessages";
import { authHref } from "@/lib/auth/redirect";
import { validateEmail } from "@/lib/auth/validation";
import { isApiError } from "@/services/apiError";
import { authService } from "@/services/authService";

/**
 * Quên mật khẩu (POST /auth/forgot-password, FR-03): nhập email → sang /reset-password?email=... nhập mã và mật khẩu mới.
 * Server luôn trả 200 kể cả email không tồn tại (chống dò email) nên form KHÔNG báo "email không có trong hệ thống";
 * chỉ lỗi gửi mail (EMAIL_SEND_FAILED) mới hiện. `redirect` (?redirect= thô) được giữ suốt luồng.
 */
export function ForgotPasswordForm({ redirect }: { redirect: string | null }) {
  const router = useRouter();
  const { status, user } = useAuth();
  const [email, setEmail] = useState("");
  const [emailError, setEmailError] = useState<string | undefined>();
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useLandingRedirect(redirect);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    const error = validateEmail(email);
    setEmailError(error);
    setFormError(null);
    if (error) return;

    setSubmitting(true);
    try {
      const address = email.trim();
      await authService.forgotPassword({ email: address });
      router.push(authHref("/reset-password", { email: address }, redirect));
    } catch (err) {
      if (isApiError(err) && err.errorCode === "VALIDATION_ERROR") setEmailError(err.fieldErrors.email);
      setFormError(getErrorMessage(err));
      setSubmitting(false);
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

  return (
    <form onSubmit={onSubmit} noValidate>
      <h1 className="cv-auth__title">Quên mật khẩu</h1>
      <p className="cv-auth__lead">Nhập email đã đăng ký, chúng tôi sẽ gửi mã để bạn đặt lại mật khẩu.</p>

      {formError && (
        <div className="cv-auth__alert" role="alert">
          {formError}
        </div>
      )}

      <Input
        label="Email"
        type="email"
        name="email"
        autoComplete="email"
        inputMode="email"
        autoFocus
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={emailError}
      />

      <Button type="submit" fullWidth loading={submitting}>
        {submitting ? "Đang gửi mã…" : "Gửi mã"}
      </Button>
      <p className="cv-auth__switch">
        <Link href={authHref("/login", {}, redirect)}>← Quay lại đăng nhập</Link>
      </p>
    </form>
  );
}
