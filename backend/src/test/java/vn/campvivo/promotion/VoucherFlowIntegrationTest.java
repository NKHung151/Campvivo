package vn.campvivo.promotion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.campvivo.auth.JwtService;
import vn.campvivo.cart.CartService;
import vn.campvivo.cart.dto.CartRequests;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.order.OrderService;
import vn.campvivo.order.OrderTimeoutJob;
import vn.campvivo.order.dto.CreateOrderResponse;
import vn.campvivo.order.dto.OrderRequests;
import vn.campvivo.payment.PaymentMethod;
import vn.campvivo.payment.PaymentResultService;
import vn.campvivo.shipping.GhnCreateOrderResult;
import vn.campvivo.shipping.GhnService;
import vn.campvivo.support.AdminMockMvc;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.support.VnpayTestSigner;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Voucher trên PostgreSQL thật (BR-5, D-29): trừ lượt khi đặt, trả lượt khi CANCELLED/PAYMENT_FAILED/hết hạn, khách dùng
 * 1 lần, đặt đồng thời không vượt lượt, API khách (/available, /public, /validate) và API admin. GHN được giả lập.
 */
@SpringBootTest
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.locations=classpath:db/migration",
        "app.jwt.secret=test_only_secret_at_least_32_characters_long",
        "app.vnpay.tmn-code=TESTTMN1",
        "app.vnpay.hash-secret=" + VoucherFlowIntegrationTest.SECRET,
        "app.vnpay.pay-url=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
        "app.vnpay.return-url=http://localhost:3000/payment/vnpay-return",
        "app.shipping.free-threshold=100000000",
        "app.order.timeout-job-delay=1h"
})
class VoucherFlowIntegrationTest {

    static final String SECRET = "TESTSECRETKEY1234567890ABCDEFGHI";
    private static final long SHIP = 30_000;

    @Autowired private OrderService orderService;
    @Autowired private CartService cartService;
    @Autowired private PaymentResultService paymentResultService;
    @Autowired private OrderTimeoutJob timeoutJob;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private GhnService ghn;

    private long productId;
    private long comboId;
    private Long customerId;

    /**
     * Dọn dữ liệu đã tạo: test catalog/combo xoá `products` ở đầu mỗi test nên không được để lại dòng nào tham chiếu tới
     * sản phẩm/combo (cart_items, order_items, combo_items, campaign_products, images). Nếu không, kết quả phụ thuộc thứ tự
     * chạy các lớp test (xanh ở máy này, đỏ ở CI).
     */
    @AfterEach
    void cleanUp() {
        jdbc.execute("TRUNCATE TABLE products, product_combos CASCADE");
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM vouchers");
        jdbc.update("DELETE FROM carts");
        jdbc.update("DELETE FROM combo_items");
        jdbc.update("DELETE FROM images");
        jdbc.update("DELETE FROM product_combos");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
        long category = jdbc.queryForObject(
                "INSERT INTO categories (name, slug, path) VALUES ('Lều', 'leu', '/') RETURNING id", Long.class);
        productId = jdbc.queryForObject("INSERT INTO products (category_id, name, slug, price, stock_quantity) "
                + "VALUES (?, 'Lều 2 người', 'leu-2-nguoi', 500000, 50) RETURNING id", Long.class, category);
        comboId = jdbc.queryForObject("INSERT INTO product_combos (category_id, name, slug, combo_price, stock_quantity) "
                + "VALUES (?, 'Combo cắm trại', 'combo-cam-trai', 900000, 10) RETURNING id", Long.class, category);
        customerId = customer("buyer@campvivo.vn").getId();
        when(ghn.calculateFee(any())).thenReturn(SHIP);
        when(ghn.createOrder(any())).thenReturn(new GhnCreateOrderResult("GHNTEST1", SHIP, null));
    }

    // ------------------------------------------------------------------ helpers

    private User customer(String email) {
        return users.findByEmail(email).orElseGet(() -> {
            User u = new User(email, "hash", "Khách " + email);
            u.setEmailVerified(true);
            return users.saveAndFlush(u);
        });
    }

    /** Voucher đang hiệu lực. `max` null = không trần. */
    private long voucher(String code, String type, long value, Long max, long minOrder, int quantity, boolean isPublic) {
        return jdbc.queryForObject("""
                INSERT INTO vouchers (code, discount_type, discount_value, max_discount, min_order_value, quantity,
                                      start_date, expiry_date, is_public)
                VALUES (?, ?, ?, ?, ?, ?, now() - interval '1 day', now() + interval '1 day', ?) RETURNING id
                """, Long.class, code, type, value, max, minOrder, quantity, isPublic);
    }

    private int usedCount(long voucherId) {
        return jdbc.queryForObject("SELECT used_count FROM vouchers WHERE id = ?", Integer.class, voucherId);
    }

    private OrderRequests.Create request(PaymentMethod method, String code, long expectedTotal, List<Long> cartItemIds) {
        return new OrderRequests.Create("Nguyễn Văn A", "0901234567", "12 Lê Lợi, Bến Nghé, Quận 1, Hồ Chí Minh",
                1442, "20109", false, method, code, null, expectedTotal, cartItemIds);
    }

    /** 1 lều 500.000, mã 10% (tối đa 50.000) → giảm 50.000, ship 30.000 → 480.000. */
    private CreateOrderResponse orderOneTent(Long userId, PaymentMethod method, String code) {
        cartService.addItem(userId, new CartRequests.AddItem(productId, null, 1));
        return orderService.create(userId, request(method, code, 480_000, null), "127.0.0.1");
    }

    private String errorOf(Runnable r) {
        try {
            r.run();
            return "OK";
        } catch (BusinessException e) {
            return e.getErrorCode();
        }
    }

    // ------------------------------------------------------------------ đặt hàng

    @Test
    void orderWithVoucherConsumesOneUseAndSnapshotsUpperCaseCode() {
        long v = voucher("SALE10", "PERCENTAGE", 10, 50_000L, 0, 5, true);

        CreateOrderResponse res = orderOneTent(customerId, PaymentMethod.COD, "  sale10 ");

        assertThat(res.finalAmount()).isEqualByComparingTo("480000");
        assertThat(usedCount(v)).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT voucher_id, voucher_code, discount_amount FROM orders WHERE id = ?", res.orderId()))
                .containsEntry("voucher_id", v).containsEntry("voucher_code", "SALE10")
                .hasEntrySatisfying("discount_amount", d -> assertThat(d.toString()).isEqualTo("50000"));
    }

    @Test
    void customerCancelReleasesUseAndCodeCanBeUsedAgain() {
        long v = voucher("SALE10", "PERCENTAGE", 10, 50_000L, 0, 5, true);
        CreateOrderResponse first = orderOneTent(customerId, PaymentMethod.COD, "SALE10");

        orderService.cancelByCustomer(customerId, first.orderId(), null);
        assertThat(usedCount(v)).isZero();

        orderOneTent(customerId, PaymentMethod.COD, "SALE10");
        assertThat(usedCount(v)).isEqualTo(1);
    }

    @Test
    void failedPaymentAndTimeoutReleaseUses() {
        long v = voucher("SALE10", "PERCENTAGE", 10, 50_000L, 0, 5, true);
        CreateOrderResponse failed = orderOneTent(customerId, PaymentMethod.VNPAY, "SALE10");
        CreateOrderResponse expired = orderOneTent(customer("late@campvivo.vn").getId(), PaymentMethod.VNPAY, "SALE10");
        assertThat(usedCount(v)).isEqualTo(2);

        paymentResultService.handlePaymentResult(PaymentMethod.VNPAY,
                VnpayTestSigner.callback(failed.orderCode(), 480_000, "24", "0", SECRET));
        assertThat(usedCount(v)).isEqualTo(1);

        jdbc.update("UPDATE orders SET created_at = now() - interval '1 day' WHERE id = ?", expired.orderId());
        assertThat(timeoutJob.cancelExpiredOrders()).isEqualTo(1);
        assertThat(usedCount(v)).isZero();
    }

    @Test
    void secondUseBySameCustomerIsRejected() {
        voucher("SALE10", "PERCENTAGE", 10, 50_000L, 0, 5, true);
        orderOneTent(customerId, PaymentMethod.COD, "SALE10");

        assertThatThrownBy(() -> orderOneTent(customerId, PaymentMethod.COD, "SALE10"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("VOUCHER_ALREADY_USED"));
    }

    @Test
    void exhaustedAndRolledBackOrderKeepsCount() {
        long v = voucher("ONE", "PERCENTAGE", 10, 50_000L, 0, 1, true);
        orderOneTent(customerId, PaymentMethod.COD, "ONE");

        assertThat(errorOf(() -> orderOneTent(customer("b@campvivo.vn").getId(), PaymentMethod.COD, "ONE")))
                .isEqualTo("VOUCHER_EXHAUSTED");

        // PRICE_CHANGED sau khi đã trừ lượt → rollback cả lượt voucher
        long v2 = voucher("TWO", "PERCENTAGE", 10, 50_000L, 0, 5, true);
        Long c = customer("c@campvivo.vn").getId();
        cartService.addItem(c, new CartRequests.AddItem(productId, null, 1));
        assertThat(errorOf(() -> orderService.create(c, request(PaymentMethod.COD, "TWO", 1, null), "127.0.0.1")))
                .isEqualTo("PRICE_CHANGED");
        assertThat(usedCount(v2)).isZero();
        assertThat(usedCount(v)).isEqualTo(1);
    }

    @Test
    void concurrentOrdersNeverExceedQuantity() throws Exception {
        long v = voucher("LAST", "PERCENTAGE", 10, 50_000L, 0, 1, true);
        List<Long> buyers = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Long id = customer("race" + i + "@campvivo.vn").getId();
            cartService.addItem(id, new CartRequests.AddItem(productId, null, 1));
            buyers.add(id);
        }
        List<String> outcomes = runConcurrently(buyers.stream().<Callable<String>>map(id ->
                () -> errorOf(() -> orderService.create(id, request(PaymentMethod.COD, "LAST", 480_000, null), "127.0.0.1")))
                .toList());

        assertThat(outcomes).filteredOn("OK"::equals).hasSize(1);
        assertThat(outcomes).filteredOn("VOUCHER_EXHAUSTED"::equals).hasSize(19);
        assertThat(usedCount(v)).isEqualTo(1);
    }

    @Test
    void sameCustomerTwoParallelOrdersUsesCodeOnce() throws Exception {
        long v = voucher("SALE10", "PERCENTAGE", 10, 50_000L, 0, 5, true);
        cartService.addItem(customerId, new CartRequests.AddItem(productId, null, 1));
        cartService.addItem(customerId, new CartRequests.AddItem(null, comboId, 1));
        List<Long> lineIds = cartService.getCart(customerId).items().stream().map(l -> l.id()).toList();
        Long tentLine = cartService.getCart(customerId).items().stream()
                .filter(l -> l.item().type().equals("PRODUCT")).findFirst().orElseThrow().id();
        Long comboLine = lineIds.stream().filter(id -> !id.equals(tentLine)).findFirst().orElseThrow();

        List<String> outcomes = runConcurrently(List.of(
                () -> errorOf(() -> orderService.create(customerId,
                        request(PaymentMethod.COD, "SALE10", 480_000, List.of(tentLine)), "127.0.0.1")),
                () -> errorOf(() -> orderService.create(customerId,
                        request(PaymentMethod.COD, "SALE10", 880_000, List.of(comboLine)), "127.0.0.1"))));

        assertThat(outcomes).containsExactlyInAnyOrder("OK", "VOUCHER_ALREADY_USED");
        assertThat(usedCount(v)).isEqualTo(1);
    }

    private static List<String> runConcurrently(List<Callable<String>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (Callable<String> t : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                return t.call();
            }));
        }
        start.countDown();
        List<String> out = new ArrayList<>();
        for (Future<String> f : futures) {
            out.add(f.get());
        }
        pool.shutdown();
        return out;
    }

    // ------------------------------------------------------------------ API khách

    @Test
    void availableListsPublicVouchersWithReasonsAndBest() throws Exception {
        voucher("SALE10", "PERCENTAGE", 10, 50_000L, 0, 5, true);        // 1 lều → giảm 50.000
        voucher("GIAM20K", "FIXED_AMOUNT", 20_000, null, 0, 5, true);     // giảm 20.000
        voucher("BIG", "FIXED_AMOUNT", 100_000, null, 800_000, 5, true);  // thiếu 300.000
        voucher("SECRET", "FIXED_AMOUNT", 90_000, null, 0, 5, false);     // ẩn
        jdbc.update("""
                INSERT INTO vouchers (code, discount_type, discount_value, quantity, start_date, expiry_date)
                VALUES ('OLD', 'PERCENTAGE', 5, 5, now() - interval '2 day', now() - interval '1 day')
                """);
        MockMvc customer = AdminMockMvc.as(Role.CUSTOMER, context, users, jwt);
        Long me = users.findByEmail("test-customer@campvivo.vn").orElseThrow().getId();
        cartService.addItem(me, new CartRequests.AddItem(productId, null, 1));

        customer.perform(get("/api/vouchers/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].code").value("SALE10"))
                .andExpect(jsonPath("$.data[0].best").value(true))
                .andExpect(jsonPath("$.data[0].estimatedDiscount").value(50000))
                .andExpect(jsonPath("$.data[1].code").value("GIAM20K"))
                .andExpect(jsonPath("$.data[1].best").value(false))
                .andExpect(jsonPath("$.data[2].code").value("BIG"))
                .andExpect(jsonPath("$.data[2].usable").value(false))
                .andExpect(jsonPath("$.data[2].reason").value("VOUCHER_MIN_ORDER_NOT_MET"))
                .andExpect(jsonPath("$.data[2].missingAmount").value(300000));

        customer.perform(post("/api/vouchers/validate").contentType("application/json").content("{\"code\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("SECRET"))
                .andExpect(jsonPath("$.data.discountAmount").value(90000))
                .andExpect(jsonPath("$.data.subtotal").value(500000))
                .andExpect(jsonPath("$.data.finalAmount").value(410000));
        customer.perform(post("/api/vouchers/validate").contentType("application/json").content("{\"code\":\"BIG\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VOUCHER_MIN_ORDER_NOT_MET"))
                .andExpect(jsonPath("$.details.minOrderValue").value(800000));
        assertThat(jdbc.queryForObject("SELECT sum(used_count) FROM vouchers", Integer.class)).isZero();
    }

    @Test
    void publicListIsAnonymousAndHidesPrivateCodes() throws Exception {
        voucher("SALE10", "PERCENTAGE", 10, 50_000L, 0, 5, true);
        voucher("SECRET", "FIXED_AMOUNT", 90_000, null, 0, 5, false);
        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class)).build();

        anonymous.perform(get("/api/vouchers/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("SALE10"));
        anonymous.perform(get("/api/vouchers/available")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ API admin

    private static final String BODY = """
            {"code":"%s","discountType":"PERCENTAGE","discountValue":10,"maxDiscount":50000,"minOrderValue":0,
             "quantity":%d,"startDate":"2026-01-01T00:00:00+07:00","expiryDate":"2099-01-01T00:00:00+07:00",
             "isActive":true,"isPublic":true}
            """;

    @Test
    void adminCrudRules() throws Exception {
        MockMvc admin = AdminMockMvc.admin(context, users, jwt);

        admin.perform(post("/api/admin/vouchers").contentType("application/json").content(BODY.formatted("new-code", 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("NEW-CODE"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        admin.perform(post("/api/admin/vouchers").contentType("application/json").content(BODY.formatted("NEW-CODE", 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("VOUCHER_CODE_ALREADY_EXISTS"));
        admin.perform(post("/api/admin/vouchers").contentType("application/json")
                        .content(BODY.formatted("BAD", 5).replace("\"discountValue\":10", "\"discountValue\":150")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fieldErrors.discountValue").exists());
        admin.perform(post("/api/admin/vouchers").contentType("application/json")
                        .content(BODY.formatted("BAD2", 5).replace("2099-01-01", "2025-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fieldErrors.expiryDate").exists());

        long used = voucher("USED", "PERCENTAGE", 10, 50_000L, 0, 5, true);
        orderOneTent(customerId, PaymentMethod.COD, "USED");
        orderOneTent(customer("d@campvivo.vn").getId(), PaymentMethod.COD, "USED");
        admin.perform(put("/api/admin/vouchers/" + used).contentType("application/json").content(BODY.formatted("USED", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fieldErrors.quantity").exists());
        admin.perform(delete("/api/admin/vouchers/" + used))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("VOUCHER_IN_USE"));

        admin.perform(get("/api/admin/vouchers").param("status", "ACTIVE").param("q", "us"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].usedCount").value(2));

        long unused = voucher("UNUSED", "PERCENTAGE", 10, null, 0, 5, true);
        admin.perform(delete("/api/admin/vouchers/" + unused)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vouchers WHERE id = ?", Integer.class, unused)).isZero();

        AdminMockMvc.as(Role.EMPLOYEE, context, users, jwt).perform(get("/api/admin/vouchers"))
                .andExpect(status().isForbidden());
    }
}
