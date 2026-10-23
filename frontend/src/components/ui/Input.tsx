import { useId } from "react";
import type { InputHTMLAttributes } from "react";

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  /** Thông báo lỗi của trường (hiển thị dưới ô, đọc được bởi trình đọc màn hình). */
  error?: string;
  hint?: string;
}

/** Ô nhập có nhãn + lỗi dùng chung cho storefront (ui-guidelines.md). */
export function Input({ label, error, hint, id, className, ...rest }: InputProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const messageId = `${inputId}-message`;
  const hasMessage = Boolean(error || hint);
  return (
    <div className={`cv-ui-field${error ? " cv-ui-field--error" : ""}${className ? ` ${className}` : ""}`}>
      <label className="cv-ui-field__label" htmlFor={inputId}>
        {label}
      </label>
      <input
        id={inputId}
        className="cv-ui-field__input"
        aria-invalid={error ? true : undefined}
        aria-describedby={hasMessage ? messageId : undefined}
        {...rest}
      />
      {hasMessage && (
        <p id={messageId} className={error ? "cv-ui-field__error" : "cv-ui-field__hint"}>
          {error ?? hint}
        </p>
      )}
    </div>
  );
}
