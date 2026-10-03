import axios from "axios";

// Xem docs/02-ky-thuat/api.md cho format response/lỗi chuẩn trước khi dùng client này
// trong services/.
export const apiClient = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080/api",
});

apiClient.interceptors.request.use((config) => {
  if (typeof window !== "undefined") {
    const token = localStorage.getItem("campvivo_token");
    if (token) config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (res) => res,
  (err) => {
    // TODO: xử lý 401 -> redirect /login theo quy ước trong .agents/rules/frontend.md
    return Promise.reject(err);
  }
);
