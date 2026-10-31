import { useId, useState } from "react";
import type { InputHTMLAttributes } from "react";

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  /** Thông báo lỗi của trường (hiển thị dưới ô, đọc được bởi trình đọc màn hình). */
  error?: string;
  hint?: string;
  /** Chỉ dùng với type="password": thêm nút Hiện/Ẩn mật khẩu. */
  revealable?: boolean;
}

/** Ô nhập có nhãn + lỗi dùng chung cho storefront (ui-guidelines.md). */
export function Input({ label, error, hint, id, className, revealable, type, ...rest }: InputProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const messageId = `${inputId}-message`;
  const hasMessage = Boolean(error || hint);
  const [revealed, setRevealed] = useState(false);
  const canReveal = Boolean(revealable) && type === "password";
  const input = (
    <input
      id={inputId}
      className="cv-ui-field__input"
      type={canReveal && revealed ? "text" : type}
      aria-invalid={error ? true : undefined}
      aria-describedby={hasMessage ? messageId : undefined}
      {...rest}
    />
  );
  return (
    <div className={`cv-ui-field${error ? " cv-ui-field--error" : ""}${className ? ` ${className}` : ""}`}>
      <label className="cv-ui-field__label" htmlFor={inputId}>
        {label}
      </label>
      {canReveal ? (
        <div className="cv-ui-field__control">
          {input}
          <button
            type="button"
            className="cv-ui-field__reveal"
            onClick={() => setRevealed((v) => !v)}
            aria-pressed={revealed}
          >
            {revealed ? "Ẩn" : "Hiện"}
          </button>
        </div>
      ) : (
        input
      )}
      {hasMessage && (
        <p id={messageId} className={error ? "cv-ui-field__error" : "cv-ui-field__hint"}>
          {error ?? hint}
        </p>
      )}
    </div>
  );
}
