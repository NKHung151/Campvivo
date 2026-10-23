import type { ButtonHTMLAttributes } from "react";

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "secondary" | "danger";
  /** Đang gửi: hiện spinner và khoá nút (chống bấm hai lần — ui-guidelines.md). */
  loading?: boolean;
  fullWidth?: boolean;
}

/** Nút dùng chung cho storefront (ui-guidelines.md). Admin dùng Button của Ant Design. */
export function Button({
  variant = "primary",
  loading = false,
  fullWidth = false,
  type = "button",
  className,
  disabled,
  children,
  ...rest
}: ButtonProps) {
  const classes = ["cv-ui-btn", `cv-ui-btn--${variant}`, fullWidth ? "cv-ui-btn--full" : "", className ?? ""]
    .filter(Boolean)
    .join(" ");
  return (
    <button type={type} className={classes} disabled={disabled || loading} aria-busy={loading || undefined} {...rest}>
      {loading && <span className="cv-ui-spinner" aria-hidden="true" />}
      {children}
    </button>
  );
}
