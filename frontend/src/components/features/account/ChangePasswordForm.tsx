"use client";

import { useState } from "react";
import type { FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { useAuth } from "@/components/features/auth/AuthProvider";
import { getErrorMessage } from "@/lib/errorMessages";
import { authHref } from "@/lib/auth/redirect";
import { validatePassword } from "@/lib/auth/validation";
import { isApiError } from "@/services/apiError";
import { userService } from "@/services/userService";

interface FieldErrors {
  currentPassword?: string;
  newPassword?: string;
}

/**
 * Đổi mật khẩu (PUT /users/me/password, FR-04). Server tăng token_version khi đổi thành công nên MỌI token cũ, kể cả của thiết
 * bị này, hết hiệu lực: form kết thúc phiên cục bộ (`clearSession`, không gọi API vì token đã vô hiệu) rồi chuyển /login?changed=1.
 */
export function ChangePasswordForm() {
  const router = useRouter();
  const { clearSession } = useAuth();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    const errors: FieldErrors = {
      currentPassword: currentPassword ? undefined : "Vui lòng nhập mật khẩu hiện tại.",
      newPassword: validatePassword(newPassword),
    };
    setFieldErrors(errors);
    setFormError(null);
    if (errors.currentPassword || errors.newPassword) return;

    setSubmitting(true);
    try {
      await userService.changePassword({ currentPassword, newPassword });
      clearSession();
      router.push(authHref("/login", { changed: "1" }));
    } catch (err) {
      if (isApiError(err) && err.errorCode === "INVALID_CURRENT_PASSWORD") {
        // Lỗi thuộc ô mật khẩu hiện tại: hiện ngay dưới ô đó và xoá để nhập lại.
        setFieldErrors({ currentPassword: getErrorMessage(err) });
        setCurrentPassword("");
      } else if (isApiError(err) && err.errorCode === "VALIDATION_ERROR") {
        setFieldErrors({ currentPassword: err.fieldErrors.currentPassword, newPassword: err.fieldErrors.newPassword });
      } else {
        setFormError(getErrorMessage(err));
      }
      setSubmitting(false);
    }
  }

  return (
    <form className="cv-account__form" onSubmit={onSubmit} noValidate>
      <p className="cv-auth__lead">
        Sau khi đổi mật khẩu, bạn sẽ bị đăng xuất khỏi mọi thiết bị và cần đăng nhập lại bằng mật khẩu mới.
      </p>
      {formError && (
        <div className="cv-auth__alert" role="alert">
          {formError}
        </div>
      )}

      <Input
        label="Mật khẩu hiện tại"
        type="password"
        name="currentPassword"
        autoComplete="current-password"
        revealable
        value={currentPassword}
        onChange={(e) => setCurrentPassword(e.target.value)}
        error={fieldErrors.currentPassword}
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

      <Button type="submit" loading={submitting}>
        {submitting ? "Đang đổi…" : "Đổi mật khẩu"}
      </Button>
    </form>
  );
}
