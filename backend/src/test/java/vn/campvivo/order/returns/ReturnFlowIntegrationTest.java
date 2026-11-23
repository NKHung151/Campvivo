package vn.campvivo.order.returns;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.campvivo.auth.JwtService;
import vn.campvivo.cart.CartService;
import vn.campvivo.cart.dto.CartRequests;
import vn.campvivo.common.upload.ImageStorage;
import vn.campvivo.order.OrderService;
import vn.campvivo.order.OrderStateService;
import vn.campvivo.order.OrderStatus;
import vn.campvivo.order.dto.CreateOrderResponse;
import vn.campvivo.order.dto.OrderRequests;
import vn.campvivo.payment.PaymentMethod;
import vn.campvivo.shipping.GhnCreateOrderResult;
import vn.campvivo.shipping.GhnService;
import vn.campvivo.support.AdminMockMvc;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Trả hàng (FR-19, BR-9, D-41) trên PostgreSQL thật: điều kiện gửi (COMPLETED, trong hạn, 1 lần, 1–2 ảnh), duyệt cộng
 * lại kho theo biến thể + nhật ký RETURN + đơn RETURNED, từ chối bắt buộc ghi chú, xử lý 2 lần, phân quyền.
 * Cloudinary và GHN được giả lập.
 */
@SpringBootTest
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.locations=classpath:db/migration",
        "app.jwt.secret=test_only_secret_at_least_32_characters_long",
        "app.shipping.free-threshold=100000000",
        "app.order.timeout-job-delay=1h",
        "app.return.window-days=7"
})
class ReturnFlowIntegrationTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CartService cartService;
    @Autowired private OrderService orderService;
    @Autowired private OrderStateService orderStateService;
    @MockBean private GhnService ghn;
    @MockBean private ImageStorage imageStorage;

    private MockMvc staff;
    private MockMvc customer;
    private MockMvc otherCustomer;
    private Long customerId;
    private long productId;
    private long variantS;
    private long variantM;

    /** Không để lại dòng tham chiếu tới đơn / sản phẩm cho các lớp test khác (chúng xoá `orders`, `products`). */
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM return_requests");
        jdbc.execute("TRUNCATE TABLE products, product_combos CASCADE");
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM return_requests");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM carts");
        jdbc.update("DELETE FROM combo_items");
        jdbc.update("DELETE FROM images");
        jdbc.update("DELETE FROM product_combos");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
        long category = jdbc.queryForObject(
                "INSERT INTO categories (name, slug, path) VALUES ('Áo', 'ao', '/') RETURNING id", Long.class);
        // Áo khoác 2 size (D-37): S tồn 3, M tồn 5
        productId = jdbc.queryForObject("INSERT INTO products (category_id, name, slug, price, stock_quantity) "
                + "VALUES (?, 'Áo khoác', 'ao-khoac', 300000, 8) RETURNING id", Long.class, category);
        long option = jdbc.queryForObject("INSERT INTO product_options (product_id, name, position) "
                + "VALUES (?, 'Size', 1) RETURNING id", Long.class, productId);
        variantS = variant(option, "S", 3, 0);
        variantM = variant(option, "M", 5, 1);

        staff = AdminMockMvc.as(Role.EMPLOYEE, context, users, jwt);
        customer = AdminMockMvc.as(Role.CUSTOMER, context, users, jwt);
        customerId = users.findByEmail("test-customer@campvivo.vn").orElseThrow().getId();
        otherCustomer = customerMvc("other-buyer@campvivo.vn");
        when(ghn.calculateFee(any())).thenReturn(30_000L);
        when(ghn.createOrder(any())).thenReturn(new GhnCreateOrderResult("GHNRET1", 30_000, null));
        when(imageStorage.store(any(), anyString())).thenReturn("https://img.test/returns/1.jpg");
    }

    private MockMvc customerMvc(String email) {
        User u = users.findByEmail(email).orElseGet(() -> {
            User created = new User(email, "hash", "Khách khác");
            created.setEmailVerified(true);
            return users.saveAndFlush(created);
        });
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.createToken(u)))
                .build();
    }

    private long variant(long option, String value, int stock, int position) {
        long valueId = jdbc.queryForObject("INSERT INTO product_option_values (option_id, value, position) "
                + "VALUES (?, ?, ?) RETURNING id", Long.class, option, value, position);
        return jdbc.queryForObject("INSERT INTO product_variants (product_id, option1_value_id, stock_quantity, position) "
                + "VALUES (?, ?, ?, ?) RETURNING id", Long.class, productId, valueId, stock, position);
    }

    /** Đơn COD: 2 áo size S, giao xong (COMPLETED). */
    private long completedOrder() {
        cartService.addItem(customerId, new CartRequests.AddItem(productId, variantS, null, 2));
        CreateOrderResponse o = orderService.create(customerId, new OrderRequests.Create("Nguyễn Văn A", "0901234567",
                "12 Lê Lợi", 1442, "20109", false, PaymentMethod.COD, null, null, 630_000L, null), "127.0.0.1");
        orderStateService.changeStatus(o.orderId(), OrderStatus.CONFIRMED, null, null);
        orderStateService.changeStatus(o.orderId(), OrderStatus.SHIPPING, null, null);
        orderStateService.changeStatus(o.orderId(), OrderStatus.COMPLETED, null, null);
        return o.orderId();
    }

    private static MockMultipartFile image(String name) {
        return new MockMultipartFile("images", name, "image/jpeg", JPEG);
    }

    private static MockMultipartHttpServletRequestBuilder returnRequest(long orderId, String reason, MockMultipartFile... images) {
        MockMultipartHttpServletRequestBuilder b = multipart("/api/orders/" + orderId + "/returns");
        for (MockMultipartFile f : images) {
            b.file(f);
        }
        if (reason != null) {
            b.param("reason", reason);
        }
        return b;
    }

    private int stock(String table, long id) {
        return jdbc.queryForObject("SELECT stock_quantity FROM " + table + " WHERE id = ?", Integer.class, id);
    }

    private String orderStatus(long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    @Test
    void customerSendsReturnOnlyForCompletedOrderWithinWindowOnce() throws Exception {
        cartService.addItem(customerId, new CartRequests.AddItem(productId, variantS, null, 1));
        long pending = orderService.create(customerId, new OrderRequests.Create("A", "0901234567", "12 Lê Lợi", 1442,
                "20109", false, PaymentMethod.COD, null, null, 330_000L, null), "127.0.0.1").orderId();
        customer.perform(returnRequest(pending, "DAMAGED", image("a.jpg")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RETURN_NOT_ALLOWED"));

        long orderId = completedOrder();
        customer.perform(get("/api/orders/" + orderId))
                .andExpect(jsonPath("$.data.canReturn").value(true))
                .andExpect(jsonPath("$.data.returnDeadline").isNotEmpty())
                .andExpect(jsonPath("$.data.returnRequest").doesNotExist());

        // Thiếu lý do / không có ảnh / quá 2 ảnh / lý do lạ → 400, không tải ảnh nào
        customer.perform(returnRequest(orderId, null, image("a.jpg")))
                .andExpect(jsonPath("$.details.fieldErrors.reason").exists());
        customer.perform(returnRequest(orderId, "DAMAGED"))
                .andExpect(jsonPath("$.details.fieldErrors.images").exists());
        customer.perform(returnRequest(orderId, "DAMAGED", image("a.jpg"), image("b.jpg"), image("c.jpg")))
                .andExpect(jsonPath("$.details.fieldErrors.images").exists());
        customer.perform(returnRequest(orderId, "BROKEN", image("a.jpg")))
                .andExpect(jsonPath("$.details.fieldErrors.reason").exists());
        verify(imageStorage, never()).store(any(), anyString());
        // Đơn của người khác → 404
        otherCustomer.perform(returnRequest(orderId, "DAMAGED", image("a.jpg"))).andExpect(status().isNotFound());

        customer.perform(returnRequest(orderId, "DAMAGED", image("a.jpg"), image("b.jpg")).param("description", "Gãy khoá kéo"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.reason").value("DAMAGED"))
                .andExpect(jsonPath("$.data.reasonLabel").value("Hàng bể vỡ"))
                .andExpect(jsonPath("$.data.description").value("Gãy khoá kéo"))
                .andExpect(jsonPath("$.data.imageUrls.length()").value(2))
                .andExpect(jsonPath("$.data.refundAmount").value(600000));   // phí ship 30.000 không hoàn

        customer.perform(returnRequest(orderId, "DAMAGED", image("a.jpg")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RETURN_ALREADY_EXISTS"));
        customer.perform(get("/api/orders/" + orderId))
                .andExpect(jsonPath("$.data.canReturn").value(false))
                .andExpect(jsonPath("$.data.returnRequest.status").value("PENDING"));
    }

    @Test
    void returnWindowExpires() throws Exception {
        long orderId = completedOrder();
        jdbc.update("UPDATE orders SET completed_at = now() - interval '8 days' WHERE id = ?", orderId);

        customer.perform(get("/api/orders/" + orderId)).andExpect(jsonPath("$.data.canReturn").value(false));
        customer.perform(returnRequest(orderId, "CHANGED_MIND", image("a.jpg")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RETURN_WINDOW_EXPIRED"));
    }

    @Test
    void approvingRestocksTheReturnedVariantAndMarksOrderReturned() throws Exception {
        long orderId = completedOrder();
        assertThat(stock("product_variants", variantS)).isEqualTo(1);
        assertThat(stock("products", productId)).isEqualTo(6);
        customer.perform(returnRequest(orderId, "WRONG_ITEM", image("a.jpg"))).andExpect(status().isCreated());
        long id = jdbc.queryForObject("SELECT id FROM return_requests WHERE order_id = ?", Long.class, orderId);

        staff.perform(get("/api/admin/returns").param("status", "PENDING"))
                .andExpect(jsonPath("$.data[0].id").value(id))
                .andExpect(jsonPath("$.data[0].customer.email").value("test-customer@campvivo.vn"))
                .andExpect(jsonPath("$.data[0].reasonLabel").value("Gửi sai hàng"));
        staff.perform(get("/api/admin/returns/" + id))
                .andExpect(jsonPath("$.data.order.items[0].variantTitle").value("S"))
                .andExpect(jsonPath("$.data.request.imageUrls[0]").value("https://img.test/returns/1.jpg"));

        staff.perform(patch("/api/admin/returns/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.order.status").value("RETURNED"));

        assertThat(orderStatus(orderId)).isEqualTo("RETURNED");
        assertThat(stock("product_variants", variantS)).isEqualTo(3);
        assertThat(stock("product_variants", variantM)).isEqualTo(5);
        assertThat(stock("products", productId)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT change_quantity FROM inventory_logs WHERE variant_id = ? AND reason = 'RETURN'",
                Integer.class, variantS)).isEqualTo(2);

        // Xử lý lần hai → 409
        staff.perform(patch("/api/admin/returns/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REJECT\",\"note\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void rejectingNeedsNoteAndKeepsOrderCompleted() throws Exception {
        long orderId = completedOrder();
        customer.perform(returnRequest(orderId, "CHANGED_MIND", image("a.jpg"))).andExpect(status().isCreated());
        long id = jdbc.queryForObject("SELECT id FROM return_requests WHERE order_id = ?", Long.class, orderId);

        staff.perform(patch("/api/admin/returns/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REJECT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fieldErrors.note").exists());
        staff.perform(patch("/api/admin/returns/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REJECT\",\"note\":\"Sản phẩm đã qua sử dụng\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request.status").value("REJECTED"));

        assertThat(orderStatus(orderId)).isEqualTo("COMPLETED");
        assertThat(stock("product_variants", variantS)).isEqualTo(1);
        customer.perform(get("/api/orders/" + orderId))
                .andExpect(jsonPath("$.data.canReturn").value(false))
                .andExpect(jsonPath("$.data.returnRequest.note").value("Sản phẩm đã qua sử dụng"));
        // Khách không gọi được API nhân viên
        customer.perform(get("/api/admin/returns")).andExpect(status().isForbidden());
    }
}
