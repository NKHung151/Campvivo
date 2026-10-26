import type { ItemSummary } from "@/types/api/catalog";
import type { ItemType, Money, OrderStatus, PaymentMethod, PaymentStatus } from "@/types/api/common";

/** Giỏ hàng, đặt hàng, thanh toán — khớp docs/02-ky-thuat/api.md mục 4.4, 4.5. */

export interface CartLine {
  id: number;
  item: ItemSummary & { isActive: boolean };
  quantity: number;
  /** Giá hiện hành × số lượng (D-04). */
  lineTotal: Money;
}

export interface Cart {
  items: CartLine[];
  subtotal: Money;
  /** Tổng số món. */
  itemCount: number;
  /** Có dòng ngừng bán / vượt số lượng khả dụng. */
  hasProblems: boolean;
}

export interface AddCartItemRequest {
  productId?: number;
  comboId?: number;
  quantity: number;
}

export interface OrderPreviewRequest {
  voucherCode?: string;
  /** Rỗng = cả giỏ. */
  cartItemIds?: number[];
  shippingDistrictId?: number;
  shippingWardCode?: string;
}

export interface OrderPreview {
  items: {
    cartItemId: number;
    type: ItemType;
    id: number;
    name: string;
    quantity: number;
    originalPrice: Money;
    unitPrice: Money;
    lineTotal: Money;
  }[];
  subtotal: Money;
  discountAmount: Money;
  shippingFee: Money;
  /** Gửi lại làm `expectedTotal` khi đặt hàng. */
  finalAmount: Money;
  voucherCode: string | null;
  /** false = chưa có địa chỉ GHN, phí ship tạm 0. */
  shippingCalculated: boolean;
  freeShipping: boolean;
  freeShippingThreshold: Money;
}

export interface CreateOrderRequest {
  recipientName: string;
  recipientPhone: string;
  /** Địa chỉ đầy đủ: số nhà, phường, quận, tỉnh. */
  shippingAddress: string;
  shippingDistrictId: number;
  shippingWardCode: string;
  saveAsDefault: boolean;
  paymentMethod: PaymentMethod;
  voucherCode?: string;
  note?: string;
  expectedTotal: Money;
  cartItemIds?: number[];
}

export interface CreateOrderResponse {
  orderId: number;
  orderCode: string;
  status: OrderStatus;
  finalAmount: Money;
  paymentMethod: PaymentMethod;
  /** VNPay: chuyển khách tới URL này. COD: null. */
  paymentUrl: string | null;
}

export interface OrderDetail {
  id: number;
  orderCode: string;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  finalAmount: Money;
  itemCount: number;
  firstItemName: string | null;
  firstItemImageUrl: string | null;
  createdAt: string;
  items: {
    type: ItemType;
    itemId: number;
    name: string;
    imageUrl: string | null;
    originalPrice: Money;
    unitPrice: Money;
    quantity: number;
    lineTotal: Money;
    canReview: boolean;
  }[];
  subtotal: Money;
  discountAmount: Money;
  shippingFee: Money;
  voucherCode: string | null;
  recipientName: string;
  recipientPhone: string;
  shippingAddress: string;
  note: string | null;
  payment: { method: PaymentMethod; status: PaymentStatus; paidAt: string | null };
  /** Vận đơn GHN (D-27): `trackingCode` null = chưa tạo. */
  shipping: { carrier: string; districtId: number | null; wardCode: string | null; trackingCode: string | null; error: string | null };
  history: { oldStatus: OrderStatus | null; newStatus: OrderStatus; note: string | null; createdAt: string }[];
  returnRequest: null;
  canCancel: boolean;
  canReturn: boolean;
  completedAt: string | null;
}

export type PaymentOutcome = "SUCCESS" | "FAILED" | "ALREADY_PROCESSED" | "INVALID_SIGNATURE" | "ORDER_NOT_FOUND" | "AMOUNT_MISMATCH";

/** GET /vnpay/return (D-28). */
export interface VnpayReturnResult {
  outcome: PaymentOutcome;
  orderId: number | null;
  orderCode: string | null;
  orderStatus: OrderStatus | null;
  paymentStatus: PaymentStatus | null;
}

/** Dòng trong danh sách đơn của khách — `OrderSummary` (api.md 4.5). */
export interface OrderSummary {
  id: number;
  orderCode: string;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  finalAmount: Money;
  itemCount: number;
  firstItemName: string | null;
  firstItemImageUrl: string | null;
  createdAt: string;
}

// ---------------------------------------------------------------- admin (api.md 4.10, FR-13)

export interface AdminOrderQuery {
  status?: OrderStatus;
  paymentMethod?: PaymentMethod;
  /** Mã đơn, SĐT người nhận, email/họ tên khách. */
  q?: string;
  /** yyyy-MM-dd (giờ Việt Nam). */
  from?: string;
  to?: string;
  page?: number;
  limit?: number;
}

export interface AdminOrderCustomer {
  id: number;
  email: string;
  fullName: string;
  phone: string | null;
}

export interface AdminOrderSummary extends OrderSummary {
  paymentStatus: PaymentStatus | null;
  customer: AdminOrderCustomer | null;
  recipientName: string;
  recipientPhone: string;
  shippingOrderCode: string | null;
}

export interface AdminOrderDetail {
  order: OrderDetail;
  customer: AdminOrderCustomer | null;
  /** Trạng thái nhân viên được chuyển tới ngay lúc này — chỉ hiện đúng các nút này. */
  allowedStatuses: OrderStatus[];
  /** Tạo / tạo lại vận đơn GHN được không (đơn CONFIRMED/SHIPPING chưa có vận đơn). */
  canCreateShipment: boolean;
}

export interface OrderStatusChange {
  orderId: number;
  oldStatus: OrderStatus;
  newStatus: OrderStatus;
  updatedAt: string;
}

/** Hành trình vận đơn đọc từ GHN; `status` là mã trạng thái GHN. */
export interface ShipmentTracking {
  carrier: string;
  trackingCode: string;
  status: string;
  expectedDeliveryTime: string | null;
  updatedAt: string | null;
  log: { status: string; updatedAt: string | null }[];
}
