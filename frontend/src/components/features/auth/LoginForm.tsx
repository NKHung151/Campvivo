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

interface FieldErrors {
  email?: string;
  password?: string;
}

/**
 * Đăng nhập bằng email + mật khẩu (POST /auth/login). Thành công → AuthProvider có `user` → điều hướng theo role
 * (`useLandingRedirect`). `redirect` là giá trị thô của ?redirect=; được kiểm tra an toàn trước khi dùng.
 * Chưa xác thực email (EMAIL_NOT_VERIFIED) → gửi lại mã rồi chuyển /verify-email (user-flows.md mục 2).
 */
export function LoginForm({ redirect }: { redirect: string | null }) {
  const router = useRouter();
  const { status, user, login, retry } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useLandingRedirect(redirect);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    const errors: FieldErrors = {};
    const emailError = validateEmail(email);
    if (emailError) errors.email = emailError;
    if (!password) errors.password = "Vui lòng nhập mật khẩu.";
    setFieldErrors(errors);
    setFormError(null);
    if (errors.email || errors.password) return;

    setSubmitting(true);
    try {
      await login(email, password);
      // Điều hướng do useLandingRedirect thực hiện khi AuthProvider cập nhật user.
    } catch (error) {
      if (isApiError(error) && error.errorCode === "EMAIL_NOT_VERIFIED") {
        await goToVerifyEmail(email.trim());
        return;
      }
      if (isApiError(error) && error.errorCode === "VALIDATION_ERROR") {
        const server = error.fieldErrors;
        setFieldErrors({ email: server.email, password: server.password });
      }
      setFormError(getErrorMessage(error));
      setSubmitting(false);
    }
  }

  /** Gửi mã mới rồi sang màn nhập mã. Lỗi gửi không chặn: màn nhập mã có nút "Gửi lại mã" và báo lỗi ở đó. */
  async function goToVerifyEmail(address: string) {
    try {
      await authService.resendCode({ email: address, purpose: "VERIFY_EMAIL" });
    } catch (resendError) {
      // OTP_RESEND_TOO_SOON: mã vừa gửi vẫn còn dùng được. Lỗi mạng/gửi mail: người dùng thử lại ở màn kế tiếp.
      if (isApiError(resendError) && resendError.errorCode === "ACCOUNT_DISABLED") {
        setFormError(getErrorMessage(resendError));
        setSubmitting(false);
        return;
      }
    }
    router.push(authHref("/verify-email", { email: address }, redirect));
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
      <h1 className="cv-auth__title">Đăng nhập</h1>
      <p className="cv-auth__lead">Đăng nhập bằng email và mật khẩu của bạn.</p>

      {status === "error" && (
        <div className="cv-auth__alert" role="alert">
          Chưa kiểm tra được phiên đăng nhập trước đó (không kết nối được máy chủ).{" "}
          <button type="button" className="cv-ui-btn cv-ui-btn--secondary" onClick={retry}>
            Thử lại
          </button>
        </div>
      )}
      {formError && (
        <div className="cv-auth__alert" role="alert">
          {formError}
        </div>
      )}

      <Input
        label="Email"
        type="email"
        name="email"
        autoComplete="username"
        inputMode="email"
        autoFocus
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={fieldErrors.email}
      />
      <Input
        label="Mật khẩu"
        type="password"
        name="password"
        autoComplete="current-password"
        revealable
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={fieldErrors.password}
      />

      <Button type="submit" fullWidth loading={submitting}>
        {submitting ? "Đang xử lý…" : "Đăng nhập"}
      </Button>
      <p className="cv-auth__switch">
        Chưa có tài khoản? <Link href={authHref("/register", {}, redirect)}>Tạo tài khoản</Link>
      </p>
      {/* TODO(N3-04): thêm liên kết "Quên mật khẩu?" (/forgot-password) khi trang đó có */}
    </form>
  );
}
