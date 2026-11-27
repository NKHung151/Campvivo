package vn.campvivo.notification;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import vn.campvivo.cart.CartService;
import vn.campvivo.cart.dto.CartRequests;
import vn.campvivo.common.upload.ImageStorage;
import vn.campvivo.order.OrderService;
import vn.campvivo.order.OrderStateService;
import vn.campvivo.order.OrderStatus;
import vn.campvivo.order.dto.CreateOrderResponse;
import vn.campvivo.order.dto.OrderRequests;
import vn.campvivo.order.returns.ReturnService;
import vn.campvivo.order.returns.dto.AdminReturnResponses;
import vn.campvivo.payment.PaymentMethod;
import vn.campvivo.payment.PaymentResultService;
import vn.campvivo.shipping.GhnCreateOrderResult;
import vn.campvivo.shipping.GhnService;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.support.VnpayTestSigner;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Thông báo đơn hàng và trả hàng (N4-04, D-42, D-43) qua luồng nghiệp vụ thật: đặt COD / VNPay (IPN ký thật), nhân viên đổi
 * trạng thái, khách huỷ, gửi / duyệt / từ chối trả hàng, transaction bị rollback. PostgreSQL thật; GHN và Cloudinary giả lập.
 * Chỉ kiểm tra thông báo của người dùng do test tạo (DB còn nhân viên của lớp test khác).
 */
@SpringBootTest
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.locations=classpath:db/migration",
        "app.jwt.secret=test_only_secret_at_least_32_characters_long",
        "app.vnpay.tmn-code=TESTTMN1",
        "app.vnpay.hash-secret=" + OrderReturnNotificationIntegrationTest.SECRET,
        "app.vnpay.pay-url=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
        "app.vnpay.return-url=http://localhost:3000/payment/vnpay-return",
        "app.shipping.free-threshold=100000000",
        "app.order.timeout-job-delay=1h",
        "app.return.window-days=7"
})
class OrderReturnNotificationIntegrationTest {

    static final String SECRET = "TESTSECRETKEY1234567890ABCDEFGHI";
    private static final long TOTAL = 630_000; // 2 × 300.000 + phí ship 30.000
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate tx;
    @Autowired private CartService cartService;
    @Autowired private OrderService orderService;
    @Autowired private OrderStateService orderStateService;
    @Autowired private PaymentResultService paymentResultService;
    @Autowired private ReturnService returnService;
    @Autowired private NotificationDispatcher dispatcher;
    @MockBean private GhnService ghn;
    @MockBean private ImageStorage imageStorage;

    private long customer;
    private long staffA;
    private long staffB;
    private long productId;
    private long variantId;

    /** Một dòng thông báo (chỉ các cột test cần). */
    private record Row(String type, Long refId, String title, String content, boolean read, boolean resolved, Long resolvedBy) {}

    @BeforeEach
    void setUp() {
        wipe();
        long category = jdbc.queryForObject(
                "INSERT INTO categories (name, slug, path) VALUES ('Lều', 'leu-nt', '/') RETURNING id", Long.class);
        productId = jdbc.queryForObject("INSERT INTO products (category_id, name, slug, price, stock_quantity) "
                + "VALUES (?, 'Lều 2 người', 'leu-2-nt', 300000, 50) RETURNING id", Long.class, category);
        long option = jdbc.queryForObject("INSERT INTO product_options (product_id, name, position) "
                + "VALUES (?, 'Màu', 1) RETURNING id", Long.class, productId);
        long value = jdbc.queryForObject("INSERT INTO product_option_values (option_id, value, position) "
                + "VALUES (?, 'Xanh', 0) RETURNING id", Long.class, option);
        variantId = jdbc.queryForObject("INSERT INTO product_variants (product_id, option1_value_id, stock_quantity, position) "
                + "VALUES (?, ?, 50, 0) RETURNING id", Long.class, productId, value);

        customer = user(Role.CUSTOMER, "Nguyễn Khách");
        staffA = user(Role.EMPLOYEE, "Trần A");
        staffB = user(Role.ADMIN, "Lê B");
        when(ghn.calculateFee(any())).thenReturn(30_000L);
        when(ghn.createOrder(any())).thenReturn(new GhnCreateOrderResult("GHNNT1", 30_000, null));
        when(imageStorage.store(any(), anyString())).thenReturn("https://img.test/returns/nt.jpg");
    }

    /** Không để lại đơn / sản phẩm / người dùng cho lớp test khác (chúng tự xoá `orders`, `products`). */
    @AfterEach
    void tearDown() {
        dispatcher.drain(Duration.ofSeconds(10));
        wipe();
        jdbc.update("DELETE FROM users WHERE email LIKE 'nt-order-%'");
    }

    private void wipe() {
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM return_requests");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM carts");
        jdbc.update("DELETE FROM inventory_logs"); // duyệt trả hàng ghi log RETURN (FK tới products và users)
        jdbc.update("DELETE FROM combo_items");
        jdbc.update("DELETE FROM images");
        jdbc.update("DELETE FROM product_combos");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
    }

    private long user(Role role, String name) {
        User u = new User("nt-order-" + UUID.randomUUID() + "@test.vn", "hash", name);
        u.setRole(role);
        u.setEmailVerified(true);
        return users.saveAndFlush(u).getId();
    }

    private CreateOrderResponse place(PaymentMethod method) {
        cartService.addItem(customer, new CartRequests.AddItem(productId, variantId, null, 2));
        return orderService.create(customer, new OrderRequests.Create("Nguyễn Khách", "0901234567", "12 Lê Lợi", 1442,
                "20109", false, method, null, null, TOTAL, null), "127.0.0.1");
    }

    private void ipn(CreateOrderResponse order, String responseCode) {
        Map<String, String> callback = VnpayTestSigner.callback(order.orderCode(), TOTAL, responseCode,
                String.valueOf(System.nanoTime() % 100_000_000), SECRET);
        paymentResultService.handlePaymentResult(PaymentMethod.VNPAY, callback);
    }

    /** Thông báo của `userId` sau khi hàng đợi thông báo đã chạy hết việc của các thao tác trước đó. */
    private List<Row> rows(long userId) {
        assertThat(dispatcher.drain(Duration.ofSeconds(10))).isTrue();
        return jdbc.query("SELECT type, ref_id, title, content, is_read, resolved_at, resolved_by FROM notifications "
                        + "WHERE user_id = ? ORDER BY id",
                (rs, i) -> new Row(rs.getString(1), rs.getObject(2, Long.class), rs.getString(3), rs.getString(4),
                        rs.getBoolean(5), rs.getObject(6) != null, rs.getObject(7, Long.class)),
                userId);
    }

    private List<Row> rows(long userId, String type) {
        return rows(userId).stream().filter(r -> r.type().equals(type)).toList();
    }

    private long completedCodOrder() {
        long id = place(PaymentMethod.COD).orderId();
        orderStateService.changeStatus(id, OrderStatus.CONFIRMED, staffA, null);
        orderStateService.changeStatus(id, OrderStatus.SHIPPING, staffA, null);
        orderStateService.changeStatus(id, OrderStatus.COMPLETED, staffA, null);
        return id;
    }

    private long sendReturn(long orderId) {
        return returnService.create(customer, orderId, "DAMAGED", "Rách một góc",
                List.of(new MockMultipartFile("images", "a.jpg", "image/jpeg", JPEG))).id();
    }

    // ------------------------------------------------------------------ đơn hàng

    @Test
    void codOrderNotifiesEveryStaffAndConfirmResolvesItForAll() {
        CreateOrderResponse order = place(PaymentMethod.COD);

        for (long staff : List.of(staffA, staffB)) {
            List<Row> fresh = rows(staff, "ORDER_NEW");
            assertThat(fresh).singleElement().satisfies(r -> {
                assertThat(r.refId()).isEqualTo(order.orderId());
                assertThat(r.title()).isEqualTo("Đơn hàng mới " + order.orderCode());
                assertThat(r.content()).startsWith("630.000đ · COD · 2 sản phẩm");
                assertThat(r.resolved()).isFalse();
            });
        }
        assertThat(rows(customer)).as("khách không nhận thông báo lúc tự đặt").isEmpty();

        orderStateService.changeStatus(order.orderId(), OrderStatus.CONFIRMED, staffA, null);

        for (long staff : List.of(staffA, staffB)) {
            assertThat(rows(staff, "ORDER_NEW")).singleElement()
                    .satisfies(r -> assertThat(r.resolvedBy()).isEqualTo(staffA));
            assertThat(rows(staff, "ORDER_PAID")).as("COD không có ORDER_PAID").isEmpty();
        }
        assertThat(rows(customer, "ORDER_STATUS")).singleElement()
                .satisfies(r -> assertThat(r.title()).isEqualTo("Đơn hàng " + order.orderCode() + " đã được xác nhận"));
    }

    @Test
    void vnpayOrderNotifiesStaffOnlyAfterPaymentAndShippingResolvesIt() {
        CreateOrderResponse order = place(PaymentMethod.VNPAY);
        assertThat(rows(staffA)).as("đơn VNPay chưa trả tiền không báo").isEmpty();

        ipn(order, "00");

        for (long staff : List.of(staffA, staffB)) {
            assertThat(rows(staff, "ORDER_PAID")).singleElement().satisfies(r -> {
                assertThat(r.title()).isEqualTo("Đơn hàng " + order.orderCode() + " đã thanh toán VNPay");
                assertThat(r.resolved()).isFalse();
            });
            assertThat(rows(staff, "ORDER_NEW")).isEmpty();
        }
        assertThat(rows(customer, "ORDER_STATUS")).singleElement()
                .satisfies(r -> assertThat(r.title()).endsWith("đã được xác nhận"));

        orderStateService.changeStatus(order.orderId(), OrderStatus.SHIPPING, staffB, null);

        for (long staff : List.of(staffA, staffB)) {
            assertThat(rows(staff, "ORDER_PAID")).singleElement()
                    .satisfies(r -> assertThat(r.resolvedBy()).isEqualTo(staffB));
        }
        assertThat(rows(customer, "ORDER_STATUS")).extracting(Row::title)
                .last().asString().endsWith("đang được giao");
    }

    @Test
    void failedVnpayPaymentNotifiesOnlyCustomer() {
        CreateOrderResponse order = place(PaymentMethod.VNPAY);
        ipn(order, "24");

        assertThat(rows(customer, "ORDER_STATUS")).singleElement()
                .satisfies(r -> assertThat(r.title()).isEqualTo("Thanh toán đơn hàng " + order.orderCode()
                        + " không thành công"));
        assertThat(rows(staffA)).isEmpty();
    }

    @Test
    void customerCancelResolvesOrderNewWithoutResolverAndDoesNotNotifyCustomer() {
        long id = place(PaymentMethod.COD).orderId();

        orderService.cancelByCustomer(customer, id, "Đặt nhầm");

        assertThat(rows(staffA, "ORDER_NEW")).singleElement().satisfies(r -> {
            assertThat(r.resolved()).isTrue();
            assertThat(r.resolvedBy()).as("khách tự huỷ → không ghi người xử lý").isNull();
        });
        assertThat(rows(customer)).isEmpty();
    }

    @Test
    void staffCancelSendsReasonToCustomer() {
        long id = place(PaymentMethod.COD).orderId();

        orderStateService.changeStatus(id, OrderStatus.CANCELLED, staffA, "Hết màu khách chọn");

        assertThat(rows(customer, "ORDER_STATUS")).singleElement()
                .satisfies(r -> assertThat(r.content()).isEqualTo("Lý do: Hết màu khách chọn"));
        assertThat(rows(staffB, "ORDER_NEW")).singleElement()
                .satisfies(r -> assertThat(r.resolvedBy()).isEqualTo(staffA));
    }

    @Test
    void completedOrderNotifiesCustomerOnEachStep() {
        long id = completedCodOrder();

        assertThat(rows(customer, "ORDER_STATUS")).extracting(Row::refId).containsOnly(id);
        assertThat(rows(customer, "ORDER_STATUS")).extracting(Row::title).satisfiesExactly(
                t -> assertThat(t).endsWith("đã được xác nhận"),
                t -> assertThat(t).endsWith("đang được giao"),
                t -> assertThat(t).endsWith("đã giao thành công"));
    }

    @Test
    void rolledBackTransactionCreatesAndResolvesNothing() {
        long id = place(PaymentMethod.COD).orderId();

        tx.executeWithoutResult(status -> {
            orderStateService.changeStatus(id, OrderStatus.CONFIRMED, staffA, null);
            status.setRollbackOnly();
        });

        assertThat(rows(staffA, "ORDER_NEW")).singleElement().satisfies(r -> assertThat(r.resolved()).isFalse());
        assertThat(rows(customer)).isEmpty();
    }

    // ------------------------------------------------------------------ trả hàng

    @Test
    void returnRequestNotifiesStaffAndRejectionResolvesAndTellsCustomerWhy() {
        long orderId = completedCodOrder();
        long returnId = sendReturn(orderId);

        for (long staff : List.of(staffA, staffB)) {
            assertThat(rows(staff, "RETURN_NEW")).singleElement().satisfies(r -> {
                assertThat(r.refId()).isEqualTo(returnId);
                assertThat(r.content()).startsWith("Lý do: ");
                assertThat(r.resolved()).isFalse();
            });
        }

        returnService.process(returnId, new AdminReturnResponses.ProcessRequest(AdminReturnResponses.Action.REJECT,
                "Ảnh không thấy lỗi"), staffB);

        for (long staff : List.of(staffA, staffB)) {
            assertThat(rows(staff, "RETURN_NEW")).singleElement()
                    .satisfies(r -> assertThat(r.resolvedBy()).isEqualTo(staffB));
        }
        assertThat(rows(customer, "RETURN_STATUS")).singleElement().satisfies(r -> {
            assertThat(r.title()).endsWith("bị từ chối");
            assertThat(r.content()).isEqualTo("Lý do: Ảnh không thấy lỗi");
        });
    }

    @Test
    void approvedReturnSendsReturnStatusButNoOrderStatusForReturned() {
        long orderId = completedCodOrder();
        long returnId = sendReturn(orderId);
        int orderStatusBefore = rows(customer, "ORDER_STATUS").size();

        returnService.process(returnId, new AdminReturnResponses.ProcessRequest(AdminReturnResponses.Action.APPROVE, null),
                staffA);

        assertThat(rows(customer, "RETURN_STATUS")).singleElement().satisfies(r -> {
            assertThat(r.title()).endsWith("đã được duyệt");
            assertThat(r.content()).isEqualTo("Số tiền hoàn dự kiến: 600.000đ.");
        });
        assertThat(rows(customer, "ORDER_STATUS")).hasSize(orderStatusBefore);
    }
}
