import { apiClient } from "@/services/apiClient";
import type { Page } from "@/types/api/common";
import type {
  AdminProductDetail,
  AdminProductQuery,
  AdminProductSummary,
  Brand,
  CategoryNode,
  ProductRequest,
} from "@/types/api/catalog";

/** API quản lý sản phẩm — docs/02-ky-thuat/api.md mục 4.10–4.11. */

export function listProducts(query: AdminProductQuery): Promise<Page<AdminProductSummary>> {
  return apiClient.getPage<AdminProductSummary>("/admin/products", { params: query });
}

export function getProduct(id: number): Promise<AdminProductDetail> {
  return apiClient.get<AdminProductDetail>(`/admin/products/${id}`);
}

export function createProduct(body: ProductRequest): Promise<AdminProductDetail> {
  return apiClient.post<AdminProductDetail>("/admin/products", body);
}

export function updateProduct(id: number, body: ProductRequest): Promise<AdminProductDetail> {
  return apiClient.put<AdminProductDetail>(`/admin/products/${id}`, body);
}

/** Ẩn/hiện (BR-14 — không xoá cứng). */
export function changeProductStatus(id: number, isActive: boolean): Promise<AdminProductSummary> {
  return apiClient.patch<AdminProductSummary>(`/admin/products/${id}/status`, { isActive });
}

export type UploadFolder = "products" | "combos" | "articles" | "campaigns";

/** Upload 1 ảnh lên Cloudinary qua backend, trả URL. */
export async function uploadImage(file: File, folder: UploadFolder = "products"): Promise<string> {
  const form = new FormData();
  form.append("file", file);
  const { url } = await apiClient.post<{ url: string }>("/admin/uploads/images", form, { params: { folder }, timeout: 60_000 });
  return url;
}

export function getCategoryTree(): Promise<CategoryNode[]> {
  return apiClient.get<CategoryNode[]>("/categories");
}

export function getBrands(): Promise<Brand[]> {
  return apiClient.get<Brand[]>("/brands");
}
