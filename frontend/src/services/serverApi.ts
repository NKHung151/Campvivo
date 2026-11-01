import type { ApiSuccessBody, Page } from "@/types/api/common";
import { API_BASE_URL } from "@/services/apiConfig";
import { ApiError, CLIENT_ERROR_CODES, networkError, toApiError } from "@/services/apiError";

/**
 * Helper gọi API phía SERVER (Server Component / SSR) cho các trang công khai (catalog, bài viết...).
 * Không gắn token: SSR không đọc được localStorage nên chỉ dùng cho endpoint công khai; trang cần
 * đăng nhập (giỏ hàng, tài khoản, admin) gọi bằng `apiClient` ở client. Lỗi cũng là `ApiError`.
 *
 * `revalidate`: giây cache của Next.js; mặc định 0 = luôn lấy dữ liệu mới (không cache).
 */
export interface ServerRequestOptions {
  params?: Record<string, string | number | boolean | undefined>;
  revalidate?: number;
}

function buildUrl(path: string, params?: ServerRequestOptions["params"]): string {
  const url = new URL(`${API_BASE_URL.replace(/\/+$/, "")}/${path.replace(/^\/+/, "")}`);
  for (const [key, value] of Object.entries(params ?? {})) {
    if (value !== undefined) url.searchParams.set(key, String(value));
  }
  return url.toString();
}

/** Lỗi điều khiển luồng của Next (nhận biết qua `digest`), không phải lỗi mạng. */
function isNextControlFlowError(error: unknown): boolean {
  const digest = (error as { digest?: unknown } | null)?.digest;
  return digest === "DYNAMIC_SERVER_USAGE" || digest === "BAILOUT_TO_CLIENT_SIDE_RENDERING";
}

async function request<T>(path: string, options: ServerRequestOptions): Promise<ApiSuccessBody<T>> {
  let response: Response;
  try {
    response = await fetch(buildUrl(path, options.params), {
      headers: { Accept: "application/json" },
      ...(options.revalidate && options.revalidate > 0
        ? { next: { revalidate: options.revalidate } }
        : { cache: "no-store" as const }),
    });
  } catch (error) {
    // Next dùng lỗi đặc biệt (fetch no-store lúc prerender → chuyển trang sang render động) để điều khiển
    // luồng; phải để nó đi tiếp, bắt nhầm thành NETWORK_ERROR sẽ làm `next build` hỏng khi không có backend.
    if (isNextControlFlowError(error)) throw error;
    throw networkError();
  }

  let body: unknown = null;
  try {
    body = await response.json();
  } catch {
    /* body không phải JSON: xử lý ở dưới như response sai format */
  }

  if (!response.ok) throw toApiError(response.status, body);
  if (typeof body !== "object" || body === null || (body as { success?: unknown }).success !== true) {
    throw new ApiError({
      status: response.status,
      errorCode: CLIENT_ERROR_CODES.UNKNOWN_ERROR,
      message: `Phản hồi không đúng định dạng (HTTP ${response.status}).`,
    });
  }
  return body as ApiSuccessBody<T>;
}

export async function serverGet<T>(path: string, options: ServerRequestOptions = {}): Promise<T> {
  return (await request<T>(path, options)).data;
}

export async function serverGetPage<T>(path: string, options: ServerRequestOptions = {}): Promise<Page<T>> {
  const body = await request<T[]>(path, options);
  if (!body.pagination) {
    throw new ApiError({
      status: 200,
      errorCode: CLIENT_ERROR_CODES.UNKNOWN_ERROR,
      message: "Phản hồi danh sách thiếu thông tin phân trang.",
    });
  }
  return { data: body.data, pagination: body.pagination };
}
