package vn.campvivo.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;
import vn.campvivo.auth.JwtService;
import vn.campvivo.cart.CartService;
import vn.campvivo.cart.dto.CartRequests;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.order.dto.CreateOrderResponse;
import vn.campvivo.order.dto.OrderRequests;
import vn.campvivo.payment.PaymentMethod;
import vn.campvivo.payment.PaymentResult;
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
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Luồng đặt hàng – thanh toán – vận đơn trên PostgreSQL thật (.agents/rules/testing.md): giữ/trừ/trả tồn kho (BR-2),
 * PRICE_CHANGED không để lại dữ liệu (BR-4), handlePaymentResult (BR-12, D-28: chữ ký, số tiền, idempotent),
 * job hết hạn (BR-3), đặt song song không bán vượt, phân quyền. GHN được giả lập.
 */
@SpringBootTest
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.locations=classpath:db/migration",
        "app.jwt.secret=test_only_secret_at_least_32_characters_long",
        "app.vnpay.tmn-code=TESTTMN1",
        "app.vnpay.hash-secret=" + OrderPaymentFlowIntegrationTest.SECRET,
        "app.vnpay.pay-url=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
        "app.vnpay.return-url=http://localhost:3000/payment/vnpay-return",
        "app.shipping.free-threshold=100000000",
        "app.order.timeout-job-delay=1h"
})
class OrderPaymentFlowIntegrationTest {

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

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM carts");
        jdbc.update("DELETE FROM combo_items");
        jdbc.update("DELETE FROM images");
        jdbc.update("DELETE FROM product_combos");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
        long category = jdbc.queryForObject(
                "INSERT INTO categories (name, slug, path) VALUES ('Lều', 'leu', '/') RETURNING id", Long.class);
        productId = jdbc.queryForObject("INSERT INTO products (category_id, name, slug, price, stock_quantity) "
                + "VALUES (?, 'Lều 2 người', 'leu-2-nguoi', 500000, 10) RETURNING id", Long.class, category);
        comboId = jdbc.queryForObject("INSERT INTO product_combos (category_id, name, slug, combo_price, stock_quantity) "
                + "VALUES (?, 'Combo cắm trại', 'combo-cam-trai', 900000, 4) RETURNING id", Long.class, category);
        customerId = customer("buyer@campvivo.vn").getId();

        when(ghn.calculateFee(any())).thenReturn(SHIP);
        when(ghn.createOrder(any())).thenReturn(new GhnCreateOrderResult("GHNTEST1", SHIP, null));
    }

    private User customer(String email) {
        return users.findByEmail(email).orElseGet(() -> {
            User u = new User(email, "hash", "Khách " + email);
            u.setEmailVerified(true);
            return users.saveAndFlush(u);
        });
    }

    private int stock(String table, long id) {
        return jdbc.queryForObject("SELECT stock_quantity FROM " + table + " WHERE id = ?", Integer.class, id);
    }

    private int reserved(String table, long id) {
        return jdbc.queryForObject("SELECT reserved_quantity FROM " + table + " WHERE id = ?", Integer.class, id);
    }

    private String orderStatus(long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private OrderRequests.Create request(PaymentMethod method, long expectedTotal) {
        return new OrderRequests.Create("Nguyễn Văn A", "0901234567", "12 Lê Lợi, Bến Nghé, Quận 1, Hồ Chí Minh",
                1442, "20109", false, method, null, null, expectedTotal, null);
    }

    /** 2 lều (1.000.000) + 1 combo (900.000) + ship 30.000. */
    private CreateOrderResponse placeOrder(Long userId, PaymentMethod method) {
        cartService.addItem(userId, new CartRequests.AddItem(productId, null, 2));
        cartService.addItem(userId, new CartRequests.AddItem(null, comboId, 1));
        return orderService.create(userId, request(method, 1_930_000), "127.0.0.1");
    }

    @Test
    void codOrderReservesStockSnapshotsItemsAndClearsCart() {
        CreateOrderResponse res = placeOrder(customerId, PaymentMethod.COD);

        assertThat(res.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(res.paymentUrl()).isNull();
        assertThat(res.finalAmount()).isEqualByComparingTo("1930000");
        assertThat(reserved("products", productId)).isEqualTo(2);
        assertThat(reserved("product_combos", comboId)).isEqualTo(1);
        assertThat(stock("products", productId)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items WHERE order_id = ?", Integer.class, res.orderId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, res.orderId())).isEqualTo("PENDING");
        assertThat(cartService.getCart(customerId).items()).isEmpty();
    }

    @Test
    void priceChangedRollsBackEverything() {
        cartService.addItem(customerId, new CartRequests.AddItem(productId, null, 1));

        assertThatThrownBy(() -> orderService.create(customerId, request(PaymentMethod.COD, 1), "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo("PRICE_CHANGED"));
        assertThat(reserved("products", productId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
        assertThat(cartService.getCart(customerId).items()).hasSize(1);
    }

    @Test
    void vnpaySuccessConfirmsOrderCommitsStockIsIdempotentAndCreatesShipment() throws Exception {
        CreateOrderResponse res = placeOrder(customerId, PaymentMethod.VNPAY);
        assertThat(res.paymentUrl()).contains("vnp_TxnRef=" + res.orderCode()).contains("vnp_Amount=193000000");

        Map<String, String> ok = VnpayTestSigner.callback(res.orderCode(), 1_930_000, "00", "14000001", SECRET);
        PaymentResult first = paymentResultService.handlePaymentResult(PaymentMethod.VNPAY, ok);
        PaymentResult second = paymentResultService.handlePaymentResult(PaymentMethod.VNPAY, ok);

        assertThat(first.outcome()).isEqualTo(PaymentResult.Outcome.SUCCESS);
        assertThat(first.orderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(second.outcome()).isEqualTo(PaymentResult.Outcome.ALREADY_PROCESSED);
        assertThat(stock("products", productId)).isEqualTo(8);
        assertThat(reserved("products", productId)).isZero();
        assertThat(stock("product_combos", comboId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT transaction_id FROM payments WHERE order_id = ?", String.class, res.orderId()))
                .isEqualTo("14000001");

        // Vận đơn GHN tạo bất đồng bộ sau commit
        String tracking = null;
        for (int i = 0; i < 50 && tracking == null; i++) {
            tracking = jdbc.queryForObject("SELECT shipping_order_code FROM orders WHERE id = ?", String.class, res.orderId());
            if (tracking == null) {
                Thread.sleep(100);
            }
        }
        assertThat(tracking).isEqualTo("GHNTEST1");
        verify(ghn, atLeastOnce()).createOrder(any());
    }

    @Test
    void invalidSignatureAndWrongAmountChangeNothing() {
        CreateOrderResponse res = placeOrder(customerId, PaymentMethod.VNPAY);
        Map<String, String> tampered = new java.util.HashMap<>(VnpayTestSigner.callback(res.orderCode(), 1_930_000, "00", "1", SECRET));
        tampered.put("vnp_Amount", "100");
        Map<String, String> wrongAmount = VnpayTestSigner.callback(res.orderCode(), 1000, "00", "2", SECRET);

        assertThat(paymentResultService.handlePaymentResult(PaymentMethod.VNPAY, tampered).outcome())
                .isEqualTo(PaymentResult.Outcome.INVALID_SIGNATURE);
        assertThat(paymentResultService.handlePaymentResult(PaymentMethod.VNPAY, wrongAmount).outcome())
                .isEqualTo(PaymentResult.Outcome.AMOUNT_MISMATCH);
        assertThat(orderStatus(res.orderId())).isEqualTo("PENDING");
        assertThat(reserved("products", productId)).isEqualTo(2);
    }

    @Test
    void failedPaymentReleasesReservation() {
        CreateOrderResponse res = placeOrder(customerId, PaymentMethod.VNPAY);

        PaymentResult r = paymentResultService.handlePaymentResult(PaymentMethod.VNPAY,
                VnpayTestSigner.callback(res.orderCode(), 1_930_000, "24", "0", SECRET));

        assertThat(r.outcome()).isEqualTo(PaymentResult.Outcome.FAILED);
        assertThat(orderStatus(res.orderId())).isEqualTo("PAYMENT_FAILED");
        assertThat(reserved("products", productId)).isZero();
        assertThat(reserved("product_combos", comboId)).isZero();
        assertThat(stock("products", productId)).isEqualTo(10);
    }

    @Test
    void timeoutJobCancelsExpiredGatewayOrdersOnly() {
        CreateOrderResponse vnpay = placeOrder(customerId, PaymentMethod.VNPAY);
        CreateOrderResponse cod = placeOrder(customer("cod@campvivo.vn").getId(), PaymentMethod.COD);
        jdbc.update("UPDATE orders SET created_at = now() - interval '1 day'");

        assertThat(timeoutJob.cancelExpiredOrders()).isEqualTo(1);
        assertThat(orderStatus(vnpay.orderId())).isEqualTo("CANCELLED");
        assertThat(orderStatus(cod.orderId())).isEqualTo("PENDING");
        assertThat(reserved("products", productId)).isEqualTo(2); // chỉ còn giữ chỗ của đơn COD
        // IPN thành công đến sau khi đã huỷ (KI-01): ghi nhận tiền, đơn giữ nguyên
        PaymentResult late = paymentResultService.handlePaymentResult(PaymentMethod.VNPAY,
                VnpayTestSigner.callback(vnpay.orderCode(), 1_930_000, "00", "14000009", SECRET));
        assertThat(late.outcome()).isEqualTo(PaymentResult.Outcome.SUCCESS);
        assertThat(orderStatus(vnpay.orderId())).isEqualTo("CANCELLED");
    }

    @Test
    void concurrentOrdersNeverOversell() throws Exception {
        jdbc.update("UPDATE products SET stock_quantity = 3 WHERE id = ?", productId);
        List<Long> buyers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Long id = customer("race" + i + "@campvivo.vn").getId();
            cartService.addItem(id, new CartRequests.AddItem(productId, null, 1));
            buyers.add(id);
        }
        ExecutorService pool = Executors.newFixedThreadPool(buyers.size());
        List<Future<String>> results = new ArrayList<>();
        for (Long id : buyers) {
            Callable<String> task = () -> {
                try {
                    orderService.create(id, request(PaymentMethod.COD, 530_000), "127.0.0.1");
                    return "OK";
                } catch (BusinessException e) {
                    return e.getErrorCode();
                }
            };
            results.add(pool.submit(task));
        }
        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : results) {
            outcomes.add(f.get());
        }
        pool.shutdown();

        assertThat(outcomes).filteredOn("OK"::equals).hasSize(3);
        assertThat(outcomes).filteredOn("OUT_OF_STOCK"::equals).hasSize(5);
        assertThat(reserved("products", productId)).isEqualTo(3);
    }

    @Test
    void ipnEndpointAnswersVnpayCodes() throws Exception {
        CreateOrderResponse res = placeOrder(customerId, PaymentMethod.VNPAY);
        MockMvc mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class)).build();
        Map<String, String> ok = VnpayTestSigner.callback(res.orderCode(), 1_930_000, "00", "14000002", SECRET);
        Map<String, String> bad = new java.util.HashMap<>(ok);
        bad.put("vnp_SecureHash", "00");

        mvc.perform(withParams(get("/api/vnpay/ipn"), bad)).andExpect(jsonPath("$.RspCode").value("97"));
        mvc.perform(withParams(get("/api/vnpay/ipn"), ok)).andExpect(jsonPath("$.RspCode").value("00"));
        mvc.perform(withParams(get("/api/vnpay/ipn"), ok)).andExpect(jsonPath("$.RspCode").value("02"));
        mvc.perform(withParams(get("/api/vnpay/return"), ok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("ALREADY_PROCESSED"))
                .andExpect(jsonPath("$.data.orderStatus").value("CONFIRMED"));
    }

    @Test
    void staffCannotPlaceOrders() throws Exception {
        MockMvc staff = AdminMockMvc.as(Role.EMPLOYEE, context, users, jwt);
        staff.perform(post("/api/orders/preview").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        staff.perform(get("/api/cart")).andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder withParams(MockHttpServletRequestBuilder b, Map<String, String> params) {
        params.forEach(b::param);
        return b;
    }
}
