/**
 * Tạo slug từ tiếng Việt — cùng quy tắc với backend `SlugUtil` ("Lều cắm trại 2 người" → "leu-cam-trai-2-nguoi").
 * Chỉ dùng để xem trước; slug thật do backend quyết định (có thể thêm hậu tố -2, -3 nếu trùng).
 */
export function toSlug(input: string): string {
  return input
    .normalize("NFD")
    .replace(/\p{M}+/gu, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

export const SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

/** Bỏ dấu + chữ thường để so khớp khi tìm (gõ "cam trai" khớp "Đồ Cắm Trại"). */
export function foldText(input: string): string {
  return input.normalize("NFD").replace(/\p{M}+/gu, "").replace(/đ/g, "d").replace(/Đ/g, "D").toLowerCase();
}
