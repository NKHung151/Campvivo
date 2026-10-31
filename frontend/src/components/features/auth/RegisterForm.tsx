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
import { validateRegister } from "@/lib/auth/validation";
import type { RegisterFieldErrors } from "@/lib/auth/validation";
import { isApiError } from "@/services/apiError";
import { authService } from "@/services/authService";

/**
 * Đăng ký (POST /auth/register, FR-01) → sang /verify-email?email=... để nhập mã OTP.
 * `redirect` (?redirect= thô) được giữ suốt luồng và kiểm tra an toàn khi dùng.
 */
export function RegisterForm({ redirect }: { redirect: string | null }) {
  const router = useRouter();
  const { status, user } = useAuth();
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<RegisterFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [emailTaken, setEmailTaken] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useLandingRedirect(redirect);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    const errors = validateRegister({ fullName, email, password });
    setFieldErrors(errors);
    setFormError(null);
    setEmailTaken(false);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    try {
      const result = await authService.register({ email: email.trim(), password, fullName: fullName.trim() });
      router.push(authHref("/verify-email", { email: result.email }, redirect));
    } catch (error) {
      if (isApiError(error) && error.errorCode === "VALIDATION_ERROR") {
        const server = error.fieldErrors;
        setFieldErrors({ fullName: server.fullName, email: server.email, password: server.password });
      }
      if (isApiError(error) && error.errorCode === "EMAIL_ALREADY_EXISTS") setEmailTaken(true);
      setFormError(getErrorMessage(error));
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
      <h1 className="cv-auth__title">Tạo tài khoản</h1>
      <p className="cv-auth__lead">Nhập thông tin để đăng ký. Chúng tôi sẽ gửi mã xác thực tới email của bạn.</p>

      {formError && (
        <div className="cv-auth__alert" role="alert">
          {formError}
          {emailTaken && (
            <>
              {" "}
              <Link href={authHref("/login", {}, redirect)}>Đăng nhập</Link>
            </>
          )}
        </div>
      )}

      <Input
        label="Họ và tên"
        name="fullName"
        autoComplete="name"
        autoFocus
        value={fullName}
        onChange={(e) => setFullName(e.target.value)}
        error={fieldErrors.fullName}
      />
      <Input
        label="Email"
        type="email"
        name="email"
        autoComplete="email"
        inputMode="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={fieldErrors.email}
      />
      <Input
        label="Mật khẩu"
        type="password"
        name="password"
        autoComplete="new-password"
        revealable
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={fieldErrors.password}
        hint="8–64 ký tự, có ít nhất 1 chữ và 1 số."
      />

      <Button type="submit" fullWidth loading={submitting}>
        {submitting ? "Đang gửi mã…" : "Đăng ký"}
      </Button>
      <p className="cv-auth__switch">
        Đã có tài khoản? <Link href={authHref("/login", {}, redirect)}>Đăng nhập</Link>
      </p>
    </form>
  );
}
