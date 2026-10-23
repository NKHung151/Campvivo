"use client";

import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { useAuth } from "@/components/features/auth/AuthProvider";
import { getErrorMessage } from "@/lib/errorMessages";
import { resolveLanding } from "@/lib/auth/redirect";
import { isApiError } from "@/services/apiError";

const EMAIL_PATTERN = /^\S+@\S+\.\S+$/;

interface FieldErrors {
  email?: string;
  password?: string;
}

/**
 * Đăng nhập bằng email + mật khẩu (POST /auth/login). Thành công → AuthProvider có `user` → điều hướng theo role
 * (`resolveLanding`). `redirect` là giá trị thô của ?redirect=; được kiểm tra an toàn trước khi dùng.
 */
export function LoginForm({ redirect }: { redirect: string | null }) {
  const router = useRouter();
  const { status, user, login, retry } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const navigated = useRef(false);

  // Đã có phiên (vừa đăng nhập hoặc vào /login khi đang đăng nhập) → về trang đích theo role. Chỉ một lần.
  useEffect(() => {
    if (status !== "authenticated" || !user || navigated.current) return;
    navigated.current = true;
    const destination = resolveLanding(user.role, redirect);
    // Vào /admin là rời root layout storefront → tải lại trang để nạp đúng CSS/layout.
    if (destination === "/admin" || destination.startsWith("/admin/")) window.location.assign(destination);
    else router.replace(destination);
  }, [status, user, redirect, router]);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    const errors: FieldErrors = {};
    if (!email.trim()) errors.email = "Vui lòng nhập email.";
    else if (!EMAIL_PATTERN.test(email.trim())) errors.email = "Email không hợp lệ.";
    if (!password) errors.password = "Vui lòng nhập mật khẩu.";
    setFieldErrors(errors);
    setFormError(null);
    if (errors.email || errors.password) return;

    setSubmitting(true);
    try {
      await login(email, password);
      // Điều hướng do effect phía trên thực hiện khi AuthProvider cập nhật user.
    } catch (error) {
      if (isApiError(error) && error.errorCode === "VALIDATION_ERROR") {
        const server = error.fieldErrors;
        setFieldErrors({ email: server.email, password: server.password });
      }
      // TODO(N3-02): EMAIL_NOT_VERIFIED → tự gọi POST /auth/resend-code rồi chuyển /verify-email?email=...
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
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={fieldErrors.password}
      />

      <Button type="submit" fullWidth loading={submitting}>
        {submitting ? "Đang đăng nhập…" : "Đăng nhập"}
      </Button>
      {/* TODO(N3-02, N3-04): thêm liên kết "Tạo tài khoản" (/register) và "Quên mật khẩu?" (/forgot-password) khi các trang đó có */}
    </form>
  );
}
