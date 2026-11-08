/** Kiểu module inventory (trang Tồn kho — FR-08, BR-8) — khớp docs/02-ky-thuat/api.md mục 4.10–4.11. */
import type { PageQuery } from "./common";

export type InventoryItemType = "PRODUCT" | "COMBO";
/** IMPORT nhập (+), EXPORT xuất (−), ADJUST kiểm kê (±), RETURN trả hàng (module đơn hàng ghi, không chọn tay). */
export type InventoryReason = "IMPORT" | "EXPORT" | "ADJUST" | "RETURN";

/** Tồn kho một biến thể; `title` null = biến thể mặc định. */
export interface InventoryVariant {
  id: number;
  title: string | null;
  sku: string | null;
  stockQuantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  isLowStock: boolean;
  isActive: boolean;
}

export interface InventoryItem {
  type: InventoryItemType;
  id: number;
  name: string;
  slug: string;
  imageUrl: string | null;
  stockQuantity: number;
  reservedQuantity: number;
  /** stock − reserved (BR-1). */
  availableQuantity: number;
  /** Combo: khả dụng ≤ ngưỡng. Sản phẩm: có biến thể đang bán khả dụng ≤ ngưỡng (gồm cả hết hàng). */
  isLowStock: boolean;
  isActive: boolean;
  /** Chỉ combo: số bộ đóng gói được ngay với hàng lẻ hiện có. */
  maxPackable: number | null;
  /** Chỉ sản phẩm: từng biến thể. Số ở trên là tồn của sản phẩm — giỏ hàng / đơn hàng dùng số này (D-37). */
  variants: InventoryVariant[] | null;
  /** Hàng đã bán qua đơn (đơn chỉ trừ tổng) chưa trừ theo biến thể — phân bổ bằng `allocateSold`. */
  unallocatedSold: number;
}

export interface InventoryQuery extends PageQuery {
  type?: InventoryItemType;
  q?: string;
  lowStock?: boolean;
  /** Mặc định AVAILABLE_ASC (ít hàng khả dụng nhất trước). */
  sort?: InventorySort;
}

export type InventorySort = "AVAILABLE_ASC" | "AVAILABLE_DESC" | "NAME_ASC" | "UPDATED_DESC";

export interface InventoryLogEntry {
  id: number;
  type: InventoryItemType;
  itemId: number;
  itemName: string;
  variantId: number | null;
  /** null với combo và biến thể mặc định. */
  variantTitle: string | null;
  /** Dương = nhập, âm = xuất. */
  changeQuantity: number;
  /** Tồn kho sau thay đổi. */
  stockAfter: number;
  reason: InventoryReason;
  note: string | null;
  user: { id: number; fullName: string };
  createdAt: string;
}

export interface InventoryLogQuery extends PageQuery {
  productId?: number;
  variantId?: number;
  comboId?: number;
  type?: InventoryItemType;
  reason?: InventoryReason;
  /** YYYY-MM-DD, giờ Việt Nam, gồm cả hai đầu. */
  from?: string;
  to?: string;
}

/**
 * Đúng một trong variantId / comboId (productId thay được variantId khi sản phẩm chỉ có 1 biến thể). IMPORT phải dương, EXPORT phải âm, ADJUST tuỳ dấu.
 * Combo chỉ được giảm — thêm bộ combo bằng "Đóng gói" (D-36).
 */
export interface AdjustmentRequest {
  productId?: number;
  variantId?: number;
  comboId?: number;
  changeQuantity: number;
  reason: Exclude<InventoryReason, "RETURN">;
  note?: string | null;
  /** true = phân bổ hàng đã bán vào biến thể: chỉ trừ biến thể (cần variantId, reason EXPORT, số âm). */
  allocateSold?: boolean;
}
