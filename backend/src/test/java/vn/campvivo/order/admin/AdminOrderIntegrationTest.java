package vn.campvivo.order.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import vn.campvivo.auth.JwtService;
import vn.campvivo.cart.CartService;
import vn.campvivo.cart.dto.CartRequests;
import vn.campvivo.order.OrderService;
import vn.campvivo.order.dto.CreateOrderResponse;
import vn.campvivo.order.dto.OrderRequests;
import vn.campvivo.payment.PaymentMethod;
import vn.campvivo.shipping.GhnCreateOrderResult;
import vn.campvivo.shipping.GhnService;
import vn.campvivo.support.AdminMockMvc;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.Role;
import vn.campvivo.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quản lý đơn (FR-13) và đơn của khách (FR-12) qua HTTP + security thật: nhân viên chỉ chuyển theo bảng database.md 5.1
 * (chỉ COD mới xác nhận/huỷ tay, huỷ bắt buộc ghi chú), COMPLETED ghi nhận đã thu tiền COD, khách huỷ đơn PENDING,
 * xem đơn người khác → 404, khách gọi API admin → 403.
 */
@SpringBootTest
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.locations=classpath:db/migration",
        "app.jwt.secret=test_only_secret_at_least_32_characters_long",
        "app.vnpay.tmn-code=TESTTMN1",
        "app.vnpay.hash-secret=TESTSECRETKEY1234567890ABCDEFGHI",
        "app.vnpay.pay-url=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
        "app.vnpay.return-url=http://localhost:3000/payment/vnpay-return",
        "app.shipping.free-threshold=100000000",
        "app.order.timeout-job-delay=1h"
})
class AdminOrderIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CartService cartService;
    @Autowired private OrderService orderService;
    @MockBean private GhnService ghn;

    private MockMvc staff;
    private MockMvc customer;
    private Long customerId;
    private long productId;

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
        staff = AdminMockMvc.as(Role.EMPLOYEE, context, users, jwt);
        customer = AdminMockMvc.as(Role.CUSTOMER, context, users, jwt);
        customerId = users.findByEmail("test-customer@campvivo.vn").orElseThrow().getId();
        when(ghn.calculateFee(any())).thenReturn(30_000L);
        when(ghn.createOrder(any())).thenReturn(new GhnCreateOrderResult("GHNCOD1", 30_000, null));
    }

    private CreateOrderResponse order(PaymentMethod method) {
        cartService.addItem(customerId, new CartRequests.AddItem(productId, null, 2));
        return orderService.create(customerId, new OrderRequests.Create("Nguyễn Văn A", "0901234567", "12 Lê Lợi",
                1442, "20109", false, method, null, null, 1_030_000L, null), "127.0.0.1");
    }

    private static String statusBody(String s, String note) {
        return note == null ? "{\"status\":\"" + s + "\"}" : "{\"status\":\"" + s + "\",\"note\":\"" + note + "\"}";
    }

    private int stock() {
        return jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id = ?", Integer.class, productId);
    }

    @Test
    void staffProcessesCodOrderEndToEnd() throws Exception {
        CreateOrderResponse o = order(PaymentMethod.COD);

        staff.perform(get("/api/admin/orders").param("q", o.orderCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orderCode").value(o.orderCode()))
                .andExpect(jsonPath("$.data[0].customer.email").value("test-customer@campvivo.vn"))
                .andExpect(jsonPath("$.data[0].paymentStatus").value("PENDING"));
        staff.perform(get("/api/admin/orders/" + o.orderId()))
                .andExpect(jsonPath("$.data.allowedStatuses[0]").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.allowedStatuses[1]").value("CANCELLED"));

        staff.perform(patch("/api/admin/orders/" + o.orderId() + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("CONFIRMED", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newStatus").value("CONFIRMED"));
        assertThat(stock()).isEqualTo(8);

        // Vận đơn GHN tạo bất đồng bộ sau commit
        String tracking = null;
        for (int i = 0; i < 50 && tracking == null; i++) {
            tracking = jdbc.queryForObject("SELECT shipping_order_code FROM orders WHERE id = ?", String.class, o.orderId());
            if (tracking == null) {
                Thread.sleep(100);
            }
        }
        assertThat(tracking).isEqualTo("GHNCOD1");

        staff.perform(patch("/api/admin/orders/" + o.orderId() + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(statusBody("SHIPPING", null))).andExpect(status().isOk());
        staff.perform(patch("/api/admin/orders/" + o.orderId() + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(statusBody("COMPLETED", null))).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, o.orderId()))
                .isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT completed_at IS NOT NULL FROM orders WHERE id = ?", Boolean.class, o.orderId()))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT note FROM order_status_history WHERE order_id = ? AND new_status = 'SHIPPING'",
                String.class, o.orderId())).contains("GHNCOD1");
    }

    @Test
    void staffCannotConfirmUnpaidVnpayOrSkipStates() throws Exception {
        CreateOrderResponse vnpay = order(PaymentMethod.VNPAY);

        staff.perform(patch("/api/admin/orders/" + vnpay.orderId() + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("CONFIRMED", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
        staff.perform(patch("/api/admin/orders/" + vnpay.orderId() + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("COMPLETED", null)))
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
        staff.perform(get("/api/admin/orders/" + vnpay.orderId())).andExpect(jsonPath("$.data.allowedStatuses").isEmpty());
    }

    @Test
    void cancellingCodRequiresNoteAndReleasesStock() throws Exception {
        CreateOrderResponse o = order(PaymentMethod.COD);

        staff.perform(patch("/api/admin/orders/" + o.orderId() + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("CANCELLED", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fieldErrors.note").exists());
        staff.perform(patch("/api/admin/orders/" + o.orderId() + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(statusBody("CANCELLED", "Khong lien lac duoc khach"))).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM products WHERE id = ?", Integer.class, productId)).isZero();
    }

    @Test
    void customerListsAndCancelsOwnPendingOrder() throws Exception {
        CreateOrderResponse o = order(PaymentMethod.COD);

        customer.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orderCode").value(o.orderCode()))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));
        customer.perform(post("/api/orders/" + o.orderId() + "/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Doi y\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.canCancel").value(false));
        customer.perform(post("/api/orders/" + o.orderId() + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_CANCELLABLE"));
    }

    @Test
    void ownershipAndRoleChecks() throws Exception {
        CreateOrderResponse o = order(PaymentMethod.COD);
        // Chuyển đơn sang chủ khác → khách hiện tại xem như không tồn tại
        AdminMockMvc.admin(context, users, jwt);
        jdbc.update("UPDATE orders SET user_id = (SELECT id FROM users WHERE email = 'test-admin@campvivo.vn') WHERE id = ?",
                o.orderId());

        customer.perform(get("/api/orders/" + o.orderId())).andExpect(status().isNotFound());
        customer.perform(post("/api/orders/" + o.orderId() + "/cancel")).andExpect(status().isNotFound());
        customer.perform(get("/api/admin/orders")).andExpect(status().isForbidden());
    }
}
