package vn.campvivo.support;

import jakarta.servlet.Filter;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.campvivo.auth.JwtService;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * MockMvc đi qua chuỗi security thật (JWT filter + @PreAuthorize) với mọi request mang sẵn token của một tài khoản.
 * Dùng cho integration test API admin: tạo (nếu chưa có) user theo email/role rồi gắn {@code Authorization: Bearer ...}
 * làm header mặc định.
 */
public final class AdminMockMvc {

    private AdminMockMvc() {
    }

    public static MockMvc as(Role role, WebApplicationContext context, UserRepository users, JwtService jwt) {
        String email = "test-" + role.name().toLowerCase() + "@campvivo.vn";
        User user = users.findByEmail(email).orElseGet(() -> {
            User u = new User(email, "hash", "Test " + role.name());
            u.setRole(role);
            u.setEmailVerified(true);
            return users.saveAndFlush(u);
        });
        Filter security = context.getBean("springSecurityFilterChain", Filter.class);
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(security)
                .defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.createToken(user)))
                .build();
    }

    public static MockMvc admin(WebApplicationContext context, UserRepository users, JwtService jwt) {
        return as(Role.ADMIN, context, users, jwt);
    }
}
