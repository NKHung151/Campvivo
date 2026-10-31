/** Kiểu module inventory (trang Tồn kho — FR-08, BR-8) — khớp docs/02-ky-thuat/api.md mục 4.10–4.11. */
import type { PageQuery } from "./common";

export type InventoryItemType = "PRODUCT" | "COMBO";
/** IMPORT nhập (+), EXPORT xuất (−), ADJUST kiểm kê (±), RETURN trả hàng (module đơn hàng ghi, không chọn tay). */
export type InventoryReason = "IMPORT" | "EXPORT" | "ADJUST" | "RETURN";

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
  /** Khả dụng ≤ ngưỡng `app.inventory.low-stock-threshold` (gồm cả hết hàng). */
  isLowStock: boolean;
  isActive: boolean;
  /** Chỉ combo: số bộ đóng gói được ngay với hàng lẻ hiện có. */
  maxPackable: number | null;
}

export interface InventoryQuery extends PageQuery {
  type?: InventoryItemType;
  q?: string;
  lowStock?: boolean;
}

export interface InventoryLogEntry {
  id: number;
  type: InventoryItemType;
  itemId: number;
  itemName: string;
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
  comboId?: number;
  type?: InventoryItemType;
  reason?: InventoryReason;
  /** YYYY-MM-DD, giờ Việt Nam, gồm cả hai đầu. */
  from?: string;
  to?: string;
}

/**
 * Đúng một trong productId / comboId. IMPORT phải dương, EXPORT phải âm, ADJUST tuỳ dấu.
 * Combo chỉ được giảm — thêm bộ combo bằng "Đóng gói" (D-27).
 */
export interface AdjustmentRequest {
  productId?: number;
  comboId?: number;
  changeQuantity: number;
  reason: Exclude<InventoryReason, "RETURN">;
  note?: string | null;
}
