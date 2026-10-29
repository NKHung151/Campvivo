package vn.campvivo.chatbot;

import org.junit.jupiter.api.Test;
import vn.campvivo.chatbot.support.IntegrationTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CB-06: migration chatbot chạy được trên DB đã có schema + seed của backend, lịch sử Flyway tách riêng.
 */
class ChatbotSchemaIT extends IntegrationTest {

    @Test
    void createsChatbotTablesAndExtensionsNextToBackendSchema() {
        List<String> tables = jdbc.sql("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name LIKE 'chatbot_%' ORDER BY table_name
                """).query(String.class).list();
        assertThat(tables).containsExactly("chatbot_conversations", "chatbot_document_chunks", "chatbot_documents",
                "chatbot_item_embeddings", "chatbot_messages");

        List<String> extensions = jdbc.sql("SELECT extname FROM pg_extension").query(String.class).list();
        assertThat(extensions).contains("vector", "pg_trgm", "unaccent");
    }

    @Test
    void keepsSeparateFlywayHistoryAndDoesNotTouchBackendHistory() {
        List<String> chatbot = jdbc.sql("SELECT version FROM flyway_schema_history_chatbot WHERE success ORDER BY installed_rank")
                .query(String.class).list();
        assertThat(chatbot).containsExactly("0", "1", "2");

        long backendRows = jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE script LIKE '%chatbot%'")
                .query(Long.class).single();
        assertThat(backendRows).isZero();
        // Seed backend vẫn nguyên
        assertThat(jdbc.sql("SELECT count(*) FROM products").query(Long.class).single()).isEqualTo(400L);
    }
}
