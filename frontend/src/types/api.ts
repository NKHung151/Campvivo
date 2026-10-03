// Kiểu dữ liệu PHẢI khớp docs/02-ky-thuat/api.md — cập nhật cả 2 nơi cùng lúc.

export interface ApiError {
  success: false;
  errorCode: string;
  message: string;
}

export interface Product {
  id: number;
  name: string;
  slug: string;
  price: number;
  stockQuantity: number;
  primaryImageUrl: string;
}
