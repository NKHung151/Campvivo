import type { ApiErrorBody } from "@/types/api/common";

/** Mã lỗi do frontend tự sinh khi không có body lỗi chuẩn của backend (không nằm trong api.md mục 2). */
export const CLIENT_ERROR_CODES = {
  /** Không kết nối được máy chủ (mạng, timeout, backend tắt). */
  NETWORK_ERROR: "NETWORK_ERROR",
  /** Response không đúng format chuẩn (vd. trang lỗi HTML từ proxy). */
  UNKNOWN_ERROR: "UNKNOWN_ERROR",
} as const;

export interface ApiErrorInit {
  status: number;
  errorCode: string;
  message: string;
  details?: Record<string, unknown>;
}

/**
 * Lỗi chuẩn của mọi lời gọi API — `{ errorCode, message, details }` theo api.md mục 1.
 * Hiển thị cho người dùng bằng `getErrorMessage()` (lib/errorMessages.ts), không in `message` thô.
 */
export class ApiError extends Error {
  /** HTTP status; 0 nếu không nhận được response. */
  readonly status: number;
  readonly errorCode: string;
  readonly details?: Record<string, unknown>;

  constructor({ status, errorCode, message, details }: ApiErrorInit) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.errorCode = errorCode;
    this.details = details;
    Object.setPrototypeOf(this, ApiError.prototype);
  }

  /** `details.fieldErrors` của lỗi VALIDATION_ERROR: { tênTrường: thôngBáo }; rỗng nếu không có. */
  get fieldErrors(): Record<string, string> {
    const raw = this.details?.fieldErrors;
    if (typeof raw !== "object" || raw === null) return {};
    const result: Record<string, string> = {};
    for (const [field, message] of Object.entries(raw)) {
      if (typeof message === "string") result[field] = message;
    }
    return result;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

function isStandardErrorBody(body: unknown): body is ApiErrorBody {
  if (typeof body !== "object" || body === null) return false;
  const candidate = body as Record<string, unknown>;
  return (
    candidate.success === false &&
    typeof candidate.errorCode === "string" &&
    typeof candidate.message === "string"
  );
}

/** Đổi (status, body) của response lỗi thành ApiError; body không đúng chuẩn → UNKNOWN_ERROR. */
export function toApiError(status: number, body: unknown): ApiError {
  if (isStandardErrorBody(body)) {
    return new ApiError({
      status,
      errorCode: body.errorCode,
      message: body.message,
      details: body.details,
    });
  }
  return new ApiError({
    status,
    errorCode: CLIENT_ERROR_CODES.UNKNOWN_ERROR,
    message: `Phản hồi lỗi không đúng định dạng (HTTP ${status}).`,
  });
}

export function networkError(): ApiError {
  return new ApiError({
    status: 0,
    errorCode: CLIENT_ERROR_CODES.NETWORK_ERROR,
    message: "Không kết nối được máy chủ.",
  });
}
