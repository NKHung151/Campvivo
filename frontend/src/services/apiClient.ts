import axios, { type AxiosRequestConfig, type AxiosResponse } from "axios";
import { clearToken, getToken } from "@/lib/authToken";
import type { ApiSuccessBody, Page } from "@/types/api/common";
import { API_BASE_URL } from "@/services/apiConfig";
import { ApiError, CLIENT_ERROR_CODES, networkError, toApiError } from "@/services/apiError";

/**
 * Client gọi API dùng chung cho CSR (docs/02-ky-thuat/api.md mục 1). Các `services/<module>Service.ts`
 * gọi qua `apiClient`, component không gọi axios/fetch trực tiếp (.agents/rules/frontend.md).
 * - Tự gắn `Authorization: Bearer <token>`.
 * - Bóc `{ success, data }` → trả thẳng `data`; danh sách phân trang dùng `getPage`.
 * - Mọi lỗi (HTTP 4xx/5xx, mạng, response sai format) đều là `ApiError`.
 * - 401 UNAUTHORIZED (token sai/hết hạn/bị thu hồi) → xoá token và báo cho `setUnauthorizedHandler`.
 *   401 INVALID_CREDENTIALS (đăng nhập sai) KHÔNG bị coi là hết phiên.
 */
const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15_000,
  headers: { Accept: "application/json" },
});

export interface UnauthorizedEvent {
  /** Request có gửi token không (false = khách gọi endpoint cần đăng nhập). */
  hadToken: boolean;
}

type UnauthorizedHandler = (event: UnauthorizedEvent) => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;

/**
 * Đăng ký hàm xử lý khi phiên hết hạn (AuthProvider sẽ chuyển người dùng về /login?redirect=...).
 * Truyền null để gỡ. Client luôn tự xoá token trước khi gọi handler.
 */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler;
}

http.interceptors.request.use((config) => {
  const token = getToken();
  if (token) config.headers.set("Authorization", `Bearer ${token}`);
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    const apiError = normalizeError(error);
    if (apiError.status === 401 && apiError.errorCode === "UNAUTHORIZED") {
      const hadToken = getToken() !== null;
      clearToken();
      unauthorizedHandler?.({ hadToken });
    }
    return Promise.reject(apiError);
  },
);

function normalizeError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  if (axios.isAxiosError(error)) {
    return error.response ? toApiError(error.response.status, error.response.data) : networkError();
  }
  return new ApiError({
    status: 0,
    errorCode: CLIENT_ERROR_CODES.UNKNOWN_ERROR,
    message: error instanceof Error ? error.message : "Lỗi không xác định.",
  });
}

function isSuccessBody<T>(body: unknown): body is ApiSuccessBody<T> {
  return typeof body === "object" && body !== null && (body as { success?: unknown }).success === true;
}

function requireSuccessBody<T>(response: AxiosResponse<unknown>): ApiSuccessBody<T> {
  if (!isSuccessBody<T>(response.data)) {
    throw toApiError(response.status, response.data);
  }
  return response.data;
}

export const apiClient = {
  /** GET → `data`. Truyền query qua `config.params`. */
  async get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return requireSuccessBody<T>(await http.get(url, config)).data;
  },

  /** GET danh sách phân trang → `{ data, pagination }`. */
  async getPage<T>(url: string, config?: AxiosRequestConfig): Promise<Page<T>> {
    const body = requireSuccessBody<T[]>(await http.get(url, config));
    if (!body.pagination) {
      throw toApiError(200, body);
    }
    return { data: body.data, pagination: body.pagination };
  },

  async post<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return requireSuccessBody<T>(await http.post(url, body, config)).data;
  },

  async put<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return requireSuccessBody<T>(await http.put(url, body, config)).data;
  },

  async patch<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return requireSuccessBody<T>(await http.patch(url, body, config)).data;
  },

  async delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return requireSuccessBody<T>(await http.delete(url, config)).data;
  },
};
