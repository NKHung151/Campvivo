import { apiClient } from "@/services/apiClient";
import type { AddCartItemRequest, Cart } from "@/types/api/order";

/** Giỏ hàng của khách đang đăng nhập — docs/02-ky-thuat/api.md mục 4.4. Mọi thao tác trả về giỏ mới nhất. */

export function getCart(): Promise<Cart> {
  return apiClient.get<Cart>("/cart");
}

export function addCartItem(body: AddCartItemRequest): Promise<Cart> {
  return apiClient.post<Cart>("/cart/items", body);
}

export function updateCartItem(id: number, quantity: number): Promise<Cart> {
  return apiClient.patch<Cart>(`/cart/items/${id}`, { quantity });
}

export function removeCartItem(id: number): Promise<Cart> {
  return apiClient.delete<Cart>(`/cart/items/${id}`);
}
