/** Địa chỉ API (kèm /api). Đặt NEXT_PUBLIC_API_URL trong .env.local; mặc định là backend chạy local. */
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080/api";
