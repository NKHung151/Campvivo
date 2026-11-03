"use client";

import { useState } from "react";
import type { FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { useAuth } from "@/components/features/auth/AuthProvider";
import { getErrorMessage } from "@/lib/errorMessages";
import { validateAddress, validateFullName, validatePhone } from "@/lib/auth/validation";
import { isApiError } from "@/services/apiError";
import { userService } from "@/services/userService";

interface FieldErrors {
  fullName?: string;
  phone?: string;
  address?: string;
}

/**
 * Sửa hồ sơ (PUT /users/me, FR-04): họ tên, số điện thoại, địa chỉ mặc định. Email chỉ đọc. Thành công → `setProfile` cập nhật
 * phiên hiện tại để tên/số điện thoại ở menu và form giao hàng đổi theo. Số điện thoại / địa chỉ để trống = xoá giá trị đã lưu.
 */
export function ProfileForm() {
  const { user, setProfile } = useAuth();
  const [fullName, setFullName] = useState(user?.fullName ?? "");
  const [phone, setPhone] = useState(user?.phone ?? "");
  const [address, setAddress] = useState(user?.address ?? "");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (!user) return null;

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    const errors: FieldErrors = {
      fullName: validateFullName(fullName),
      phone: validatePhone(phone),
      address: validateAddress(address),
    };
    setFieldErrors(errors);
    setFormError(null);
    setNotice(null);
    if (errors.fullName || errors.phone || errors.address) return;

    setSubmitting(true);
    try {
      const saved = await userService.updateProfile({
        fullName: fullName.trim(),
        phone: phone.trim() || null,
        address: address.trim() || null,
      });
      setProfile(saved);
      setFullName(saved.fullName);
      setPhone(saved.phone ?? "");
      setAddress(saved.address ?? "");
      setNotice("Đã lưu thay đổi.");
    } catch (err) {
      if (isApiError(err) && err.errorCode === "VALIDATION_ERROR") {
        setFieldErrors({
          fullName: err.fieldErrors.fullName,
          phone: err.fieldErrors.phone,
          address: err.fieldErrors.address,
        });
      }
      setFormError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="cv-account__form" onSubmit={onSubmit} noValidate>
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

      <Input label="Email" type="email" name="email" value={user.email} readOnly disabled hint="Email không thể thay đổi." />
      <Input
        label="Họ và tên"
        name="fullName"
        autoComplete="name"
        value={fullName}
        onChange={(e) => setFullName(e.target.value)}
        error={fieldErrors.fullName}
      />
      <Input
        label="Số điện thoại"
        type="tel"
        name="phone"
        autoComplete="tel"
        inputMode="tel"
        maxLength={10}
        value={phone}
        onChange={(e) => setPhone(e.target.value)}
        error={fieldErrors.phone}
        hint="Không bắt buộc. 10 số, bắt đầu bằng 0."
      />
      <Input
        label="Địa chỉ mặc định"
        name="address"
        autoComplete="street-address"
        value={address}
        onChange={(e) => setAddress(e.target.value)}
        error={fieldErrors.address}
        hint="Không bắt buộc. Dùng để điền sẵn khi đặt hàng."
      />

      <Button type="submit" loading={submitting}>
        {submitting ? "Đang lưu…" : "Lưu thay đổi"}
      </Button>
    </form>
  );
}
