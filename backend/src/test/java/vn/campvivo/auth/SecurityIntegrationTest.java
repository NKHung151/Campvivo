package vn.campvivo.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chuỗi bảo mật đầy đủ trên PostgreSQL thật: JWT filter đọc bảng users (token_version, is_active, role).
 * @Transactional: dữ liệu rollback sau mỗi test; MockMvc chạy cùng thread nên filter thấy thay đổi chưa commit.
 * ddl-auto=none vì các bảng module khác chưa có migration.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.jwt.secret=test_secret_with_at_least_32_characters_ok",
        "app.cors.allowed-origins=http://localhost:3000"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
@Transactional
class SecurityIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;

    private User saveUser(String email, Role role) {
        User user = new User(email, "hash", "Test");
        user.setRole(role);
        return users.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwt.createToken(user);
    }

    // --- 401: thiếu / sai token ---

    @Test
    void noTokenOnProtectedEndpointIsUnauthorizedJson() throws Exception {
        mvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void garbageTokenIsUnauthorizedEvenOnPublicEndpoint() throws Exception {
        mvc.perform(get("/api/products/anything").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void staleTokenVersionIsUnauthorized() throws Exception {
        User user = saveUser("stale@campvivo.vn", Role.CUSTOMER);
        String token = bearer(user);
        user.setTokenVersion(user.getTokenVersion() + 1);
        users.saveAndFlush(user);

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void lockedUserIsUnauthorized() throws Exception {
        User user = saveUser("locked@campvivo.vn", Role.CUSTOMER);
        String token = bearer(user);
        user.setActive(false);
        users.saveAndFlush(user);

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletedUserIsUnauthorized() throws Exception {
        User user = saveUser("gone@campvivo.vn", Role.CUSTOMER);
        String token = bearer(user);
        users.delete(user);
        users.flush();

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    // --- 403: sai role ---

    @Test
    void customerOnAdminEndpointIsForbiddenJson() throws Exception {
        User customer = saveUser("c1@campvivo.vn", Role.CUSTOMER);

        mvc.perform(get("/api/admin/orders").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void staffOnCustomerOnlyEndpointIsForbidden() throws Exception {
        User employee = saveUser("e1@campvivo.vn", Role.EMPLOYEE);

        mvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void roleIsTakenFromDatabaseNotFromToken() throws Exception {
        User user = saveUser("demoted@campvivo.vn", Role.ADMIN);
        String adminToken = bearer(user);
        user.setRole(Role.CUSTOMER);
        users.saveAndFlush(user);

        mvc.perform(get("/api/admin/orders").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isForbidden());
    }

    // --- Được phép đi qua security (không có controller nên là 404, nhưng KHÔNG phải 401/403) ---

    @Test
    void employeeAndAdminPassAdminPathRule() throws Exception {
        for (Role role : new Role[] {Role.EMPLOYEE, Role.ADMIN}) {
            User staff = saveUser(role.name().toLowerCase() + "@campvivo.vn", role);
            mvc.perform(get("/api/admin/orders").header(HttpHeaders.AUTHORIZATION, bearer(staff)))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void publicCatalogNeedsNoToken() throws Exception {
        // /api/categories chưa có controller (N2-01) và bảng catalog chưa tồn tại: chỉ kiểm tra luật security
        mvc.perform(get("/api/categories"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anyRoleCanReachUserScopedEndpointsPastSecurity() throws Exception {
        User employee = saveUser("e2@campvivo.vn", Role.EMPLOYEE);

        // /api/notifications chưa có controller (N4-03): qua được security nên là 404, không phải 401/403
        mvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isNotFound());
    }

    // --- CORS ---

    @Test
    void corsPreflightFromFrontendOriginIsAllowed() throws Exception {
        mvc.perform(options("/api/orders")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    void corsPreflightFromOtherOriginIsRejected() throws Exception {
        mvc.perform(options("/api/orders")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }
}
