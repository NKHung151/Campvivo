package vn.campvivo.notification;

import org.junit.jupiter.api.Test;
import vn.campvivo.order.OrderStatus;
import vn.campvivo.promotion.DiscountType;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationMessagesTest {

    @Test
    void money() {
        assertThat(NotificationMessages.money(new BigDecimal("1250000"))).isEqualTo("1.250.000đ");
        assertThat(NotificationMessages.money(new BigDecimal("500"))).isEqualTo("500đ");
        assertThat(NotificationMessages.money(BigDecimal.ZERO)).isEqualTo("0đ");
        assertThat(NotificationMessages.money(null)).isEqualTo("0đ");
    }

    @Test
    void excerptCollapsesWhitespaceAndCuts() {
        assertThat(NotificationMessages.excerpt("  Xin   chào\n\nshop  ")).isEqualTo("Xin chào shop");
        String long150 = "a".repeat(150);
        String cut = NotificationMessages.excerpt(long150);
        assertThat(cut).hasSize(NotificationMessages.EXCERPT_LENGTH).endsWith("…");
        assertThat(NotificationMessages.excerpt(null)).isEmpty();
    }

    @Test
    void orderStatusPerStatus() {
        NotificationMessage m = NotificationMessages.orderStatus(108, "ORD-20261127-001", OrderStatus.SHIPPING, null);
        assertThat(m.type()).isEqualTo(NotificationType.ORDER_STATUS);
        assertThat(m.title()).isEqualTo("Đơn hàng ORD-20261127-001 đang được giao");
        assertThat(m.link()).isEqualTo("/account/orders/108");
        assertThat(m.refType()).isEqualTo(NotificationRefType.ORDER);
        assertThat(m.refId()).isEqualTo(108L);
        assertThat(m.groupKey()).isNull();

        assertThat(NotificationMessages.orderStatus(1, "X", OrderStatus.CANCELLED, " Hết hàng ").content())
                .isEqualTo("Lý do: Hết hàng");
        assertThat(NotificationMessages.orderStatus(1, "X", OrderStatus.CANCELLED, "  ").content())
                .isEqualTo("Đơn hàng của bạn đã bị huỷ.");
        for (OrderStatus s : new OrderStatus[]{OrderStatus.CONFIRMED, OrderStatus.COMPLETED, OrderStatus.PAYMENT_FAILED}) {
            assertThat(NotificationMessages.orderStatus(1, "X", s, null).title()).contains("X");
        }
    }

    @Test
    void orderStatusRejectsStatusesThatAreNotNotified() {
        Arrays.asList(OrderStatus.PENDING, OrderStatus.RETURNED).forEach(s ->
                assertThatThrownBy(() -> NotificationMessages.orderStatus(1, "X", s, null))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void returnStatus() {
        NotificationMessage ok = NotificationMessages.returnStatus(5, 108, "X", true, null, new BigDecimal("990000"));
        assertThat(ok.title()).isEqualTo("Yêu cầu trả hàng đơn X đã được duyệt");
        assertThat(ok.content()).isEqualTo("Số tiền hoàn dự kiến: 990.000đ.");
        assertThat(ok.link()).isEqualTo("/account/orders/108");
        assertThat(ok.refType()).isEqualTo(NotificationRefType.RETURN);
        assertThat(ok.refId()).isEqualTo(5L);

        NotificationMessage no = NotificationMessages.returnStatus(5, 108, "X", false, "Ảnh không rõ", null);
        assertThat(no.title()).endsWith("bị từ chối");
        assertThat(no.content()).isEqualTo("Lý do: Ảnh không rõ");
    }

    @Test
    void supportMessagesAreGroupedPerCustomer() {
        NotificationMessage reply = NotificationMessages.supportReply(42, "Shop đã nhận được  yêu cầu");
        assertThat(reply.type()).isEqualTo(NotificationType.SUPPORT_MESSAGE);
        assertThat(reply.groupKey()).isEqualTo("SUPPORT:42");
        assertThat(reply.content()).isEqualTo("Shop đã nhận được yêu cầu");
        assertThat(reply.link()).isEqualTo("/account/support");

        NotificationMessage incoming = NotificationMessages.supportNew(42, "Nguyễn An", "Lều còn size 4 người không?");
        assertThat(incoming.type()).isEqualTo(NotificationType.SUPPORT_NEW);
        assertThat(incoming.title()).isEqualTo("Nguyễn An đã nhắn tin hỗ trợ");
        assertThat(incoming.groupKey()).isEqualTo("SUPPORT_NEW:42");
        assertThat(incoming.refType()).isEqualTo(NotificationRefType.CONVERSATION);
        assertThat(incoming.refId()).isEqualTo(42L);
        assertThat(incoming.link()).isEqualTo("/admin/support?customer=42");
    }

    @Test
    void voucherNew() {
        OffsetDateTime expiry = OffsetDateTime.of(2026, 12, 31, 20, 0, 0, 0, ZoneOffset.UTC); // 01/01/2027 03:00 giờ VN
        NotificationMessage pct = NotificationMessages.voucherNew(7, "CAMP10", DiscountType.PERCENTAGE, new BigDecimal("10"),
                new BigDecimal("100000"), new BigDecimal("500000"), expiry);
        assertThat(pct.title()).isEqualTo("Mã giảm giá mới: CAMP10");
        assertThat(pct.content()).isEqualTo("Giảm 10% (tối đa 100.000đ) cho đơn từ 500.000đ. Hạn dùng đến 01/01/2027.");
        assertThat(pct.refType()).isEqualTo(NotificationRefType.VOUCHER);

        NotificationMessage fixed = NotificationMessages.voucherNew(8, "GIAM50K", DiscountType.FIXED_AMOUNT,
                new BigDecimal("50000"), null, BigDecimal.ZERO, expiry);
        assertThat(fixed.content()).isEqualTo("Giảm 50.000đ. Hạn dùng đến 01/01/2027.");
    }

    @Test
    void favoriteMessages() {
        NotificationMessage one = NotificationMessages.favoriteOnSale(3, "leu-x", "Lều X", new BigDecimal("2000000"),
                new BigDecimal("1600000"));
        assertThat(one.title()).isEqualTo("Lều X đang giảm giá");
        assertThat(one.content()).isEqualTo("Giá chỉ còn 1.600.000đ (giá gốc 2.000.000đ, giảm 20%).");
        assertThat(one.link()).isEqualTo("/products/leu-x");

        NotificationMessage many = NotificationMessages.favoritesOnSale(9, "sale-he", "Sale hè", 3);
        assertThat(many.title()).isEqualTo("3 sản phẩm bạn yêu thích đang giảm giá");
        assertThat(many.link()).isEqualTo("/promotions/sale-he");
        assertThat(many.refType()).isEqualTo(NotificationRefType.CAMPAIGN);

        NotificationMessage back = NotificationMessages.backInStock(NotificationRefType.COMBO, 4, "combo-a", "Combo A");
        assertThat(back.link()).isEqualTo("/combos/combo-a");
        assertThat(back.groupKey()).isEqualTo("BACK_IN_STOCK:COMBO:4");
        assertThatThrownBy(() -> NotificationMessages.backInStock(NotificationRefType.ORDER, 4, "x", "X"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void discountPercentRoundsHalfUp() {
        assertThat(NotificationMessages.discountPercent(new BigDecimal("300000"), new BigDecimal("199000"))).isEqualTo(34);
        assertThat(NotificationMessages.discountPercent(BigDecimal.ZERO, BigDecimal.ZERO)).isZero();
    }

    @Test
    void staffOrderAndReturnMessages() {
        NotificationMessage cod = NotificationMessages.orderNew(108, "X", new BigDecimal("1250000"), 3);
        assertThat(cod.type()).isEqualTo(NotificationType.ORDER_NEW);
        assertThat(cod.content()).isEqualTo("1.250.000đ · COD · 3 sản phẩm. Cần xác nhận.");
        assertThat(cod.link()).isEqualTo("/admin/orders/108");

        NotificationMessage paid = NotificationMessages.orderPaid(109, "Y", new BigDecimal("990000"));
        assertThat(paid.type()).isEqualTo(NotificationType.ORDER_PAID);
        assertThat(paid.title()).isEqualTo("Đơn hàng Y đã thanh toán VNPay");

        NotificationMessage ret = NotificationMessages.returnNew(5, "X", "Sản phẩm bị lỗi");
        assertThat(ret.link()).isEqualTo("/admin/returns/5");
        assertThat(ret.content()).startsWith("Lý do: Sản phẩm bị lỗi");
    }

    @Test
    void lowStockGroupedPerItemWithShortSearchLink() {
        NotificationMessage low = NotificationMessages.lowStock(NotificationRefType.PRODUCT, 12, "Bếp gas Y", 3, 5);
        assertThat(low.title()).isEqualTo("Bếp gas Y sắp hết hàng");
        assertThat(low.content()).startsWith("Còn 3 khả dụng (ngưỡng cảnh báo 5)");
        assertThat(low.groupKey()).isEqualTo("LOW_STOCK:PRODUCT:12");
        assertThat(URLDecoder.decode(low.link(), StandardCharsets.UTF_8)).isEqualTo("/admin/inventory?tab=products&q=Bếp gas Y");

        NotificationMessage out = NotificationMessages.lowStock(NotificationRefType.COMBO, 4, "Combo A", 0, 5);
        assertThat(out.title()).isEqualTo("Combo A đã hết hàng");
        assertThat(out.link()).startsWith("/admin/inventory?tab=combos&q=");

        String longName = "Lều cắm trại siêu nhẹ chống thấm ".repeat(8); // ~260 ký tự có dấu
        NotificationMessage longLink = NotificationMessages.lowStock(NotificationRefType.PRODUCT, 1, longName.strip(), 1, 5);
        assertThat(longLink.link().length()).isLessThanOrEqualTo(NotificationMessage.MAX_LINK);
    }

    @Test
    void reviewFlaggedIsOneSharedGroup() {
        NotificationMessage m = NotificationMessages.reviewFlagged(4);
        assertThat(m.title()).isEqualTo("Có 4 đánh giá cần kiểm duyệt");
        assertThat(m.groupKey()).isEqualTo("REVIEW_FLAGGED");
        assertThat(m.refType()).isEqualTo(NotificationRefType.REVIEW_QUEUE);
        assertThat(m.refId()).isNull();
    }

    @Test
    void messageCutsLongTitleAndContent() {
        NotificationMessage m = new NotificationMessage(NotificationType.ORDER_NEW, "t".repeat(300), "c".repeat(1200), null,
                null, null, null);
        assertThat(m.title()).hasSize(NotificationMessage.MAX_TITLE).endsWith("…");
        assertThat(m.content()).hasSize(NotificationMessage.MAX_CONTENT).endsWith("…");
    }

    @Test
    void everyStaffTypeIsActionableAndEveryCustomerTypeIsNot() {
        for (NotificationType t : NotificationType.values()) {
            assertThat(t.actionable()).as(t.name()).isEqualTo(t.audience() == NotificationType.Audience.STAFF);
        }
    }
}
