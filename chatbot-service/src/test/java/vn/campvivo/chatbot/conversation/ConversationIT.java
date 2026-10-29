package vn.campvivo.chatbot.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import vn.campvivo.chatbot.support.IntegrationTest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CB-21/22/23: hội thoại của khách, trả lời SSE (delta → cards → sources → done / error), lưu tin + lịch sử gửi kèm
 * cho model, giới hạn tần suất, quyền chủ hội thoại.
 */
class ConversationIT extends IntegrationTest {

    private static final String BASE = "/api/chatbot/conversations";
    private static final String ACCEPT = "text/event-stream, application/json";

    @Autowired
    ObjectMapper objectMapper;

    private long customer;
    private String auth;

    @BeforeEach
    void setUp() {
        chatModel.reset();
        customer = createUser("CUSTOMER");
        auth = bearer(customer);
    }

    private String newConversation(String authorization) throws Exception {
        String body = mvc.perform(post(BASE).header("Authorization", authorization))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value(ConversationRepository.DEFAULT_TITLE))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private record Event(String name, JsonNode data) {}

    /** Gửi tin và đọc toàn bộ sự kiện SSE. */
    private List<Event> send(String conversationId, String content) throws Exception {
        MvcResult result = mvc.perform(post(BASE + "/" + conversationId + "/messages").header("Authorization", auth)
                        .header("Accept", ACCEPT).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConversationController.SendMessageRequest(content))))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult(10_000);
        return parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private List<Event> parse(String body) throws Exception {
        List<Event> events = new ArrayList<>();
        for (String block : body.split("\n\n")) {
            String name = null;
            StringBuilder data = new StringBuilder();
            for (String line : block.split("\n")) {
                if (line.startsWith("event:")) {
                    name = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    data.append(line.substring(5));
                }
            }
            if (name != null) {
                events.add(new Event(name, objectMapper.readTree(data.toString())));
            }
        }
        return events;
    }

    private JsonNode messages(String conversationId) throws Exception {
        String body = mvc.perform(get(BASE + "/" + conversationId + "/messages").header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    @Test
    void streamsAnswerThenSavesBothMessagesAndSetsTitle() throws Exception {
        String id = newConversation(auth);
        chatModel.reply = "Chào bạn, mình có thể giúp gì?";

        List<Event> events = send(id, "Xin chào trợ lý");

        List<String> names = events.stream().map(Event::name).toList();
        assertThat(names).first().isEqualTo("delta");
        assertThat(names.subList(names.size() - 3, names.size())).containsExactly("cards", "sources", "done");
        String streamed = events.stream().filter(e -> e.name().equals("delta"))
                .map(e -> e.data().path("text").asText()).reduce("", String::concat);
        assertThat(streamed).isEqualTo("Chào bạn, mình có thể giúp gì?");

        JsonNode saved = messages(id);
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).path("role").asText()).isEqualTo("USER");
        assertThat(saved.get(1).path("role").asText()).isEqualTo("ASSISTANT");
        assertThat(saved.get(1).path("content").asText()).isEqualTo(streamed);
        assertThat(saved.get(1).path("id").asLong())
                .isEqualTo(events.get(events.size() - 1).data().path("messageId").asLong());
        assertThat(saved.get(1).path("createdAt").asText()).endsWith("+07:00");

        mvc.perform(get(BASE).header("Authorization", auth))
                .andExpect(jsonPath("$.data[0].id").value(id))
                .andExpect(jsonPath("$.data[0].title").value("Xin chào trợ lý"));
    }

    @Test
    void sendsPreviousTurnsAsHistory() throws Exception {
        String id = newConversation(auth);
        send(id, "Câu hỏi thứ nhất");
        chatModel.reply = "Trả lời thứ hai";
        send(id, "Câu hỏi thứ hai");

        List<Message> prompt = chatModel.lastPrompt.getInstructions();
        assertThat(prompt.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(prompt.get(0).getText()).contains("Trợ lý Campvivo");
        List<String> rest = prompt.subList(1, prompt.size()).stream().map(Message::getText).toList();
        assertThat(rest).containsExactly("Câu hỏi thứ nhất", "Xin chào, mình là trợ lý Campvivo.", "Câu hỏi thứ hai");
    }

    @Test
    void modelFailureSendsErrorEventAndKeepsOnlyTheQuestion() throws Exception {
        String id = newConversation(auth);
        chatModel.fail = true;

        List<Event> events = send(id, "Có lều không?");

        assertThat(events).extracting(Event::name).containsExactly("error");
        assertThat(events.get(0).data().path("errorCode").asText()).isEqualTo("AI_UNAVAILABLE");
        JsonNode saved = messages(id);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).path("role").asText()).isEqualTo("USER");
    }

    @Test
    void validatesContentBeforeOpeningTheStream() throws Exception {
        String id = newConversation(auth);
        mvc.perform(post(BASE + "/" + id + "/messages").header("Authorization", auth).header("Accept", ACCEPT)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fieldErrors.content").exists());
        mvc.perform(post(BASE + "/" + id + "/messages").header("Authorization", auth).header("Accept", ACCEPT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + "a".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conversationsOfOtherCustomersAreNotFound() throws Exception {
        String other = bearer(createUser("CUSTOMER"));
        String id = newConversation(other);

        mvc.perform(get(BASE + "/" + id + "/messages").header("Authorization", auth))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        mvc.perform(post(BASE + "/" + id + "/messages").header("Authorization", auth).header("Accept", ACCEPT)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "/" + id).header("Authorization", auth)).andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "/" + id).header("Authorization", other)).andExpect(status().isNoContent());
        mvc.perform(get(BASE + "/" + UUID.randomUUID() + "/messages").header("Authorization", other))
                .andExpect(status().isNotFound());
    }

    @Test
    void rateLimitReturns429WithRetryAfter() throws Exception {
        String id = newConversation(auth);
        for (int i = 0; i < 20; i++) {
            jdbc.sql("INSERT INTO chatbot_messages (conversation_id, role, content) VALUES (?, 'USER', 'spam')")
                    .param(UUID.fromString(id)).update();
        }
        mvc.perform(post(BASE + "/" + id + "/messages").header("Authorization", auth).header("Accept", ACCEPT)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"thêm một câu\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("CHAT_RATE_LIMITED"))
                .andExpect(jsonPath("$.details.retryAfterSeconds").isNumber());
    }
}
