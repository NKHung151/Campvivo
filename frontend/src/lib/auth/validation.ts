/*
 * Kiểm tra form xác thực phía client — chỉ để báo lỗi sớm, backend luôn kiểm tra lại và là bên quyết định.
 * Quy tắc khớp RegisterRequest.java / docs/02-ky-thuat/api.md mục 4.1. Hàm thuần để kiểm chứng độc lập.
 */

export const EMAIL_PATTERN = /^\S+@\S+\.\S+$/;
export const OTP_LENGTH = 6;

/** Giới hạn BCrypt: tối đa 72 byte UTF-8 (ký tự có dấu chiếm 2–3 byte). */
const PASSWORD_MAX_BYTES = 72;

export interface RegisterFields {
  fullName: string;
  email: string;
  password: string;
}

export type RegisterFieldErrors = Partial<Record<keyof RegisterFields, string>>;

export function validateEmail(email: string): string | undefined {
  const value = email.trim();
  if (!value) return "Vui lòng nhập email.";
  if (!EMAIL_PATTERN.test(value) || value.length > 255) return "Email không hợp lệ.";
  return undefined;
}

export function validatePassword(password: string): string | undefined {
  if (!password) return "Vui lòng nhập mật khẩu.";
  if (password.length < 8 || password.length > 64) return "Mật khẩu phải từ 8 đến 64 ký tự.";
  if (!/\p{L}/u.test(password) || !/\d/.test(password)) return "Mật khẩu phải có ít nhất 1 chữ và 1 số.";
  if (new TextEncoder().encode(password).length > PASSWORD_MAX_BYTES) {
    return "Mật khẩu quá dài (tối đa 72 byte; ký tự có dấu chiếm nhiều byte hơn).";
  }
  return undefined;
}

export function validateFullName(fullName: string): string | undefined {
  const length = fullName.trim().length;
  if (length === 0) return "Vui lòng nhập họ tên.";
  if (length < 2 || length > 100) return "Họ tên phải từ 2 đến 100 ký tự.";
  return undefined;
}

export function validateRegister(fields: RegisterFields): RegisterFieldErrors {
  const errors: RegisterFieldErrors = {
    fullName: validateFullName(fields.fullName),
    email: validateEmail(fields.email),
    password: validatePassword(fields.password),
  };
  for (const key of Object.keys(errors) as (keyof RegisterFields)[]) {
    if (errors[key] === undefined) delete errors[key];
  }
  return errors;
}

/** Mã OTP: đúng 6 chữ số. */
export function validateOtp(code: string): string | undefined {
  if (!code) return "Vui lòng nhập mã xác thực.";
  if (!new RegExp(`^\\d{${OTP_LENGTH}}$`).test(code)) return `Mã xác thực gồm ${OTP_LENGTH} chữ số.`;
  return undefined;
}

/** Chỉ giữ chữ số, tối đa 6 ký tự (dán "123 456" vẫn dùng được). */
export function normalizeOtpInput(raw: string): string {
  return raw.replace(/\D/g, "").slice(0, OTP_LENGTH);
}
