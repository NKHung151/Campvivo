import axios, { type AxiosRequestConfig, type AxiosResponse } from "axios";
import { getToken } from "@/lib/authToken";
import type { ApiSuccessBody, Page } from "@/types/api/common";
import { CHATBOT_API_URL } from "@/services/apiConfig";
import { ApiError, CLIENT_ERROR_CODES, networkError, toApiError } from "@/services/apiError";
import { authService } from "@/services/authService";

/**
 * Client gọi chatbot-service (FR-28, D-28) — cùng quy ước với `apiClient` (gắn token, bóc `data`, lỗi → ApiError) nhưng
 * trỏ `CHATBOT_API_URL`. Khi gộp chatbot vào backend thì xoá file này, các service dùng thẳng `apiClient`.
 * 401 UNAUTHORIZED: hỏi lại backend `/users/me` qua `apiClient` để luồng hết phiên chung (AuthProvider) xử lý.
 */
const http = axios.create({
  baseURL: CHATBOT_API_URL,
  timeout: 30_000,
  headers: { Accept: "application/json" },
});

http.interceptors.request.use((config) => {
  const token = getToken();
  if (token) config.headers.set("Authorization", `Bearer ${token}`);
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    const apiError = normalizeError(error);
    if (apiError.status === 401 && apiError.errorCode === "UNAUTHORIZED") reportUnauthorized();
    return Promise.reject(apiError);
  },
);

/** Token bị chatbot từ chối → nhờ backend xác nhận; nếu backend cũng từ chối, apiClient tự đăng xuất. */
export function reportUnauthorized(): void {
  authService.getMe().catch(() => undefined);
}

export function normalizeError(error: unknown): ApiError {
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

function requireSuccessBody<T>(response: AxiosResponse<unknown>): ApiSuccessBody<T> {
  const body = response.data as { success?: unknown };
  if (typeof body !== "object" || body === null || body.success !== true) {
    throw toApiError(response.status, response.data);
  }
  return response.data as ApiSuccessBody<T>;
}

export const chatbotHttp = {
  async get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return requireSuccessBody<T>(await http.get(url, config)).data;
  },

  async getPage<T>(url: string, config?: AxiosRequestConfig): Promise<Page<T>> {
    const body = requireSuccessBody<T[]>(await http.get(url, config));
    if (!body.pagination) throw toApiError(200, body);
    return { data: body.data, pagination: body.pagination };
  },

  /** Tải file (blob) — vd. file gốc của tài liệu. */
  async getBlob(url: string): Promise<{ blob: Blob; fileName: string | null }> {
    const res = await http.get<Blob>(url, { responseType: "blob" });
    const disposition = String(res.headers["content-disposition"] ?? "");
    const match = /filename\*=UTF-8''([^;]+)/i.exec(disposition) ?? /filename="?([^";]+)"?/i.exec(disposition);
    return { blob: res.data, fileName: match ? decodeURIComponent(match[1]) : null };
  },

  async post<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return requireSuccessBody<T>(await http.post(url, body, config)).data;
  },

  async put<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return requireSuccessBody<T>(await http.put(url, body, config)).data;
  },

  /** DELETE trả 204 không có body. */
  async delete(url: string): Promise<void> {
    await http.delete(url);
  },
};
