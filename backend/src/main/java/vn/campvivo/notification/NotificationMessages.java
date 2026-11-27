package vn.campvivo.notification;

import vn.campvivo.order.OrderStatus;
import vn.campvivo.promotion.DiscountType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Dựng title / content / link / khoá gộp cho từng loại thông báo (D-42, D-43) — một chỗ duy nhất, hàm thuần (dễ unit test).
 * Link là route frontend (docs/03-san-pham/pages.md). Không chứa dữ liệu nhạy cảm (chỉ mã đơn, tên mặt hàng, số tiền).
 */
public final class NotificationMessages {

    /** Trích tin nhắn chat tối đa ngần này ký tự. */
    static final int EXCERPT_LENGTH = 100;
    /** Số ký tự đầu của tên mặt hàng đưa vào ô tìm của link Tồn kho. */
    static final int SEARCH_PREFIX = 50;

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private NotificationMessages() {
    }

    // ------------------------------------------------------------------ khách

    /** `ORDER_STATUS` cho CONFIRMED, SHIPPING, COMPLETED, CANCELLED, PAYMENT_FAILED (D-42; trạng thái khác không gửi). */
    public static NotificationMessage orderStatus(long orderId, String orderCode, OrderStatus status, String note) {
        String title;
        String content;
        switch (status) {
            case CONFIRMED -> {
                title = "Đơn hàng " + orderCode + " đã được xác nhận";
                content = "Đơn hàng đang được chuẩn bị để giao.";
            }
            case SHIPPING -> {
                title = "Đơn hàng " + orderCode + " đang được giao";
                content = "Đơn vị vận chuyển đã nhận hàng và đang giao đến bạn.";
            }
            case COMPLETED -> {
                title = "Đơn hàng " + orderCode + " đã giao thành công";
                content = "Cảm ơn bạn đã mua sắm tại Campvivo. Hãy đánh giá sản phẩm để giúp người mua khác nhé.";
            }
            case CANCELLED -> {
                title = "Đơn hàng " + orderCode + " đã bị huỷ";
                content = isBlank(note) ? "Đơn hàng của bạn đã bị huỷ." : "Lý do: " + note.strip();
            }
            case PAYMENT_FAILED -> {
                title = "Thanh toán đơn hàng " + orderCode + " không thành công";
                content = "Giao dịch chưa hoàn tất nên đơn hàng đã bị huỷ. Bạn có thể đặt lại đơn mới.";
            }
            default -> throw new IllegalArgumentException("Không gửi ORDER_STATUS cho trạng thái " + status);
        }
        return new NotificationMessage(NotificationType.ORDER_STATUS, title, content, "/account/orders/" + orderId,
                NotificationRefType.ORDER, orderId, null);
    }

    /** `RETURN_STATUS`: duyệt kèm số tiền hoàn dự kiến; từ chối kèm lý do (bắt buộc ở nghiệp vụ). */
    public static NotificationMessage returnStatus(long returnId, long orderId, String orderCode, boolean approved,
                                                   String note, BigDecimal refundAmount) {
        String title;
        String content;
        if (approved) {
            title = "Yêu cầu trả hàng đơn " + orderCode + " đã được duyệt";
            content = "Số tiền hoàn dự kiến: " + money(refundAmount) + "."
                    + (isBlank(note) ? "" : " Ghi chú: " + note.strip());
        } else {
            title = "Yêu cầu trả hàng đơn " + orderCode + " bị từ chối";
            content = isBlank(note) ? "Yêu cầu trả hàng của bạn không được chấp nhận." : "Lý do: " + note.strip();
        }
        return new NotificationMessage(NotificationType.RETURN_STATUS, title, content, "/account/orders/" + orderId,
                NotificationRefType.RETURN, returnId, null);
    }

    /** `SUPPORT_MESSAGE`: gộp theo hội thoại tới khi khách đọc. */
    public static NotificationMessage supportReply(long customerId, String latestMessage) {
        return new NotificationMessage(NotificationType.SUPPORT_MESSAGE, "Nhân viên hỗ trợ đã trả lời bạn",
                excerpt(latestMessage), "/account/support", NotificationRefType.CONVERSATION, customerId,
                "SUPPORT:" + customerId);
    }

    /** `VOUCHER_NEW` (mã công khai vừa hiệu lực — D-45). */
    public static NotificationMessage voucherNew(long voucherId, String code, DiscountType discountType,
                                                 BigDecimal discountValue, BigDecimal maxDiscount,
                                                 BigDecimal minOrderValue, OffsetDateTime expiryDate) {
        String discount = discountType == DiscountType.PERCENTAGE
                ? "Giảm " + discountValue.stripTrailingZeros().toPlainString() + "%"
                        + (maxDiscount == null ? "" : " (tối đa " + money(maxDiscount) + ")")
                : "Giảm " + money(discountValue);
        String condition = minOrderValue == null || minOrderValue.signum() <= 0
                ? "" : " cho đơn từ " + money(minOrderValue);
        String content = discount + condition + ". Hạn dùng đến " + date(expiryDate) + ".";
        return new NotificationMessage(NotificationType.VOUCHER_NEW, "Mã giảm giá mới: " + code, content, "/promotions",
                NotificationRefType.VOUCHER, voucherId, null);
    }

    /** `FAVORITE_ON_SALE` — một sản phẩm yêu thích vào chiến dịch. */
    public static NotificationMessage favoriteOnSale(long productId, String slug, String productName,
                                                     BigDecimal originalPrice, BigDecimal salePrice) {
        String content = "Giá chỉ còn " + money(salePrice) + " (giá gốc " + money(originalPrice) + ", giảm "
                + discountPercent(originalPrice, salePrice) + "%).";
        return new NotificationMessage(NotificationType.FAVORITE_ON_SALE, productName + " đang giảm giá", content,
                "/products/" + slug, NotificationRefType.PRODUCT, productId, null);
    }

    /** `FAVORITE_ON_SALE` — nhiều sản phẩm yêu thích cùng vào một chiến dịch: gộp thành một thông báo. */
    public static NotificationMessage favoritesOnSale(long campaignId, String campaignSlug, String campaignName, int count) {
        return new NotificationMessage(NotificationType.FAVORITE_ON_SALE,
                count + " sản phẩm bạn yêu thích đang giảm giá",
                "Trong chương trình \"" + campaignName + "\". Xem ngay kẻo lỡ!", "/promotions/" + campaignSlug,
                NotificationRefType.CAMPAIGN, campaignId, null);
    }

    /**
     * `FAVORITE_BACK_IN_STOCK` — `itemType` là PRODUCT hoặc COMBO. Gộp theo mặt hàng tới khi khách đọc: kho dao động 0 → 1 → 0
     * → 1 (huỷ đơn rồi có người mua) chỉ để lại một dòng chưa đọc.
     */
    public static NotificationMessage backInStock(NotificationRefType itemType, long itemId, String slug, String name) {
        NotificationRefType type = requireItem(itemType);
        return new NotificationMessage(NotificationType.FAVORITE_BACK_IN_STOCK, name + " đã có hàng trở lại",
                "Mặt hàng bạn yêu thích đã có hàng. Đặt ngay trước khi hết nhé.", itemPath(type) + slug,
                type, itemId, "BACK_IN_STOCK:" + type.name() + ":" + itemId);
    }

    // ------------------------------------------------------------------ nhân viên

    /** `ORDER_NEW` — đơn COD mới (đơn VNPay chưa trả tiền không báo — D-42). */
    public static NotificationMessage orderNew(long orderId, String orderCode, BigDecimal finalAmount, int itemCount) {
        return new NotificationMessage(NotificationType.ORDER_NEW, "Đơn hàng mới " + orderCode,
                money(finalAmount) + " · COD · " + itemCount + " sản phẩm. Cần xác nhận.", "/admin/orders/" + orderId,
                NotificationRefType.ORDER, orderId, null);
    }

    /** `ORDER_PAID` — đơn VNPay đã thanh toán (tự CONFIRMED), chờ bàn giao vận chuyển. */
    public static NotificationMessage orderPaid(long orderId, String orderCode, BigDecimal finalAmount) {
        return new NotificationMessage(NotificationType.ORDER_PAID, "Đơn hàng " + orderCode + " đã thanh toán VNPay",
                money(finalAmount) + " · Cần bàn giao cho đơn vị vận chuyển.", "/admin/orders/" + orderId,
                NotificationRefType.ORDER, orderId, null);
    }

    /** `RETURN_NEW`. */
    public static NotificationMessage returnNew(long returnId, String orderCode, String reasonLabel) {
        return new NotificationMessage(NotificationType.RETURN_NEW, "Yêu cầu trả hàng đơn " + orderCode,
                "Lý do: " + reasonLabel + ". Cần duyệt hoặc từ chối.", "/admin/returns/" + returnId,
                NotificationRefType.RETURN, returnId, null);
    }

    /** `SUPPORT_NEW`: gộp theo khách tới khi có nhân viên trả lời. */
    public static NotificationMessage supportNew(long customerId, String customerName, String latestMessage) {
        return new NotificationMessage(NotificationType.SUPPORT_NEW, customerName + " đã nhắn tin hỗ trợ",
                excerpt(latestMessage), "/admin/support?customer=" + customerId, NotificationRefType.CONVERSATION,
                customerId, "SUPPORT_NEW:" + customerId);
    }

    /** `LOW_STOCK` (D-44): khả dụng ≤ ngưỡng, 0 = hết hàng. Gộp theo mặt hàng tới khi khả dụng vượt ngưỡng. */
    public static NotificationMessage lowStock(NotificationRefType itemType, long itemId, String name, int available,
                                               int threshold) {
        NotificationRefType type = requireItem(itemType);
        String title = available <= 0 ? name + " đã hết hàng" : name + " sắp hết hàng";
        String content = available <= 0
                ? "Không còn hàng khả dụng (ngưỡng cảnh báo " + threshold + "). Cần nhập thêm."
                : "Còn " + available + " khả dụng (ngưỡng cảnh báo " + threshold + "). Cần nhập thêm.";
        // Ô tìm của trang Tồn kho khớp "chứa": phần đầu tên là đủ, và giữ link dưới 500 ký tự sau khi mã hoá.
        String query = name.length() <= SEARCH_PREFIX ? name : name.substring(0, SEARCH_PREFIX).stripTrailing();
        String link = "/admin/inventory?tab=" + (type == NotificationRefType.COMBO ? "combos" : "products") + "&q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);
        return new NotificationMessage(NotificationType.LOW_STOCK, title, content, link, type, itemId,
                "LOW_STOCK:" + type.name() + ":" + itemId);
    }

    /** `REVIEW_FLAGGED`: một dòng chung cho hàng chờ kiểm duyệt, nội dung là số đánh giá đang chờ. */
    public static NotificationMessage reviewFlagged(long pendingCount) {
        return new NotificationMessage(NotificationType.REVIEW_FLAGGED,
                "Có " + pendingCount + " đánh giá cần kiểm duyệt",
                "Đánh giá chứa từ ngữ nhạy cảm đang bị làm mờ. Hãy xem rồi ẩn hoặc đánh dấu an toàn.",
                "/admin/reviews?status=SENSITIVE", NotificationRefType.REVIEW_QUEUE, null, "REVIEW_FLAGGED");
    }

    // ------------------------------------------------------------------ định dạng

    /** 1250000 → "1.250.000đ" (tiền là số nguyên VND). */
    static String money(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        DecimalFormat format = new DecimalFormat("#,##0", symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(amount == null ? BigDecimal.ZERO : amount) + "đ";
    }

    /** Gộp khoảng trắng, cắt tối đa {@link #EXCERPT_LENGTH} ký tự kèm "…". */
    static String excerpt(String text) {
        String s = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        return s.length() <= EXCERPT_LENGTH ? s : s.substring(0, EXCERPT_LENGTH - 1).stripTrailing() + "…";
    }

    static String date(OffsetDateTime t) {
        return t.atZoneSameInstant(VN).format(DATE);
    }

    /** Phần trăm giảm, làm tròn HALF_UP; giá gốc 0 → 0. */
    static int discountPercent(BigDecimal original, BigDecimal sale) {
        if (original == null || original.signum() <= 0 || sale == null) {
            return 0;
        }
        return original.subtract(sale).multiply(BigDecimal.valueOf(100))
                .divide(original, 0, RoundingMode.HALF_UP).intValue();
    }

    private static String itemPath(NotificationRefType itemType) {
        return requireItem(itemType) == NotificationRefType.COMBO ? "/combos/" : "/products/";
    }

    private static NotificationRefType requireItem(NotificationRefType itemType) {
        if (itemType != NotificationRefType.PRODUCT && itemType != NotificationRefType.COMBO) {
            throw new IllegalArgumentException("Mặt hàng phải là PRODUCT hoặc COMBO: " + itemType);
        }
        return itemType;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
