package vn.campvivo.chatbot.security;

import org.junit.jupiter.api.Test;
import vn.campvivo.chatbot.support.IntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CB-07: JWT của backend dùng được ở chatbot; chỉ CUSTOMER chat, chỉ ADMIN quản lý tài liệu; đăng xuất (tăng
 * token_version) hoặc khoá tài khoản ở backend → token mất hiệu lực ngay.
 */
class ChatbotSecurityIT extends IntegrationTest {

    private static final String CHAT = "/api/chatbot/conversations";
    private static final String DOCS = "/api/admin/chatbot/documents";

    @Test
    void noTokenOrBadTokenIsUnauthorized() throws Exception {
        mvc.perform(get(CHAT)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
        mvc.perform(get(CHAT).header("Authorization", "Bearer khong-phai-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyCustomerCanChatAndOnlyAdminCanManageDocuments() throws Exception {
        long customer = createUser("CUSTOMER");
        long employee = createUser("EMPLOYEE");
        long admin = createUser("ADMIN");

        mvc.perform(get(CHAT).header("Authorization", bearer(customer))).andExpect(status().isOk());
        mvc.perform(get(CHAT).header("Authorization", bearer(admin))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        mvc.perform(get(DOCS).header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get(DOCS).header("Authorization", bearer(employee))).andExpect(status().isForbidden());
        mvc.perform(get(DOCS).header("Authorization", bearer(customer))).andExpect(status().isForbidden());
    }

    @Test
    void logoutOrLockInBackendRevokesToken() throws Exception {
        long customer = createUser("CUSTOMER");
        String old = bearer(customer);

        jdbc.sql("UPDATE users SET token_version = token_version + 1 WHERE id = ?").param(customer).update();
        mvc.perform(get(CHAT).header("Authorization", old)).andExpect(status().isUnauthorized());

        String fresh = bearer(customer);
        jdbc.sql("UPDATE users SET is_active = false WHERE id = ?").param(customer).update();
        mvc.perform(get(CHAT).header("Authorization", fresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPathsAreDenied() throws Exception {
        long customer = createUser("CUSTOMER");
        mvc.perform(get("/api/products").header("Authorization", bearer(customer))).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
