/** Địa chỉ API (kèm /api). Đặt NEXT_PUBLIC_API_URL trong .env.local; mặc định là backend chạy local. */
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080/api";

/** Chatbot service (FR-28, D-28) chạy riêng cổng 8090; khi gộp vào backend thì dùng chung API_BASE_URL. */
export const CHATBOT_API_URL = process.env.NEXT_PUBLIC_CHATBOT_API_URL ?? "http://localhost:8090/api";
