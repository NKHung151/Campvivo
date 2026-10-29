package vn.campvivo.chatbot.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import vn.campvivo.chatbot.support.IntegrationTest;
import vn.campvivo.chatbot.support.SampleFiles;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CB-13/14/15: admin thêm/sửa/bật-tắt/thay file/nạp lại/xoá tài liệu; bot chỉ truy hồi tài liệu đang bật và READY.
 */
class AdminDocumentIT extends IntegrationTest {

    private static final String BASE = "/api/admin/chatbot/documents";

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    KnowledgeSearch search;

    private String admin;

    @BeforeEach
    void setUp() {
        admin = bearer(createUser("ADMIN"));
        embeddingModel.fail = false;
    }

    private JsonNode upload(MockMultipartFile file, String title) throws Exception {
        var req = multipart(BASE).file(file).header("Authorization", admin);
        if (title != null) {
            req.param("title", title);
        }
        String body = mvc.perform(req).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    /** Chờ xử lý nền xong (tối đa ~10 giây). */
    private JsonNode awaitProcessed(long id) throws Exception {
        for (int i = 0; i < 100; i++) {
            JsonNode d = detail(id);
            if (!"PROCESSING".equals(d.path("status").asText())) {
                return d;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Tài liệu " + id + " xử lý quá lâu");
    }

    private JsonNode detail(long id) throws Exception {
        String body = mvc.perform(get(BASE + "/" + id).header("Authorization", admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    private static String unique(String text) {
        return text + "\nMa tai lieu: " + UUID.randomUUID();
    }

    @Test
    void pdfIsSplitByPageAndSearchableWithSource() throws Exception {
        byte[] pdf = SampleFiles.pdf(List.of(
                unique("Chinh sach doi tra Campvivo\nKhach duoc doi tra san pham trong vong 7 ngay ke tu ngay nhan hang."),
                "Bao hanh\nSan pham leu va tui ngu duoc bao hanh 12 thang voi loi tu nha san xuat."));
        JsonNode doc = upload(new MockMultipartFile("file", "chinh-sach.pdf", "application/pdf", pdf), "Chính sách");
        JsonNode done = awaitProcessed(doc.path("id").asLong());

        assertThat(done.path("status").asText()).isEqualTo("READY");
        assertThat(done.path("chunkCount").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(done.path("chunks").get(0).path("page").asInt()).isEqualTo(1);
        assertThat(done.path("title").asText()).isEqualTo("Chính sách");

        var hits = search.search("bao hanh leu tui ngu 12 thang");
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).title()).isEqualTo("Chính sách");
        assertThat(hits.get(0).page()).isEqualTo(2);
    }

    @Test
    void docxAndMarkdownAreAccepted() throws Exception {
        byte[] docx = SampleFiles.docx(List.of(unique("Hướng dẫn chọn túi ngủ"),
                "Túi ngủ mùa đông nên có nhiệt độ dễ chịu từ 0 độ C trở xuống khi đi núi cao."));
        JsonNode d1 = upload(new MockMultipartFile("file", "tui-ngu.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx), null);
        assertThat(d1.path("title").asText()).isEqualTo("tui-ngu");          // không nhập tiêu đề → tên file
        assertThat(awaitProcessed(d1.path("id").asLong()).path("status").asText()).isEqualTo("READY");

        byte[] md = unique("# Checklist cắm trại\n- Lều\n- Túi ngủ\n- Đèn pin và pin dự phòng").getBytes(StandardCharsets.UTF_8);
        JsonNode d2 = upload(new MockMultipartFile("file", "checklist.md", "text/markdown", md), "Checklist");
        assertThat(awaitProcessed(d2.path("id").asLong()).path("status").asText()).isEqualTo("READY");
    }

    @Test
    void rejectsBadFiles() throws Exception {
        mvc.perform(multipart(BASE).file(new MockMultipartFile("file", "a.exe", "application/octet-stream",
                        new byte[]{1, 2, 3})).header("Authorization", admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_FILE_TYPE"));
        // Đổi đuôi: chữ thường đặt tên .pdf
        mvc.perform(multipart(BASE).file(new MockMultipartFile("file", "gia.pdf", "application/pdf",
                        "khong phai pdf".getBytes())).header("Authorization", admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_FILE_TYPE"));
        mvc.perform(multipart(BASE).header("Authorization", admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fieldErrors.file").exists());

        byte[] txt = unique("Trung lap").getBytes(StandardCharsets.UTF_8);
        JsonNode first = upload(new MockMultipartFile("file", "a.txt", "text/plain", txt), "A");
        mvc.perform(multipart(BASE).file(new MockMultipartFile("file", "b.txt", "text/plain", txt))
                        .header("Authorization", admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("DOCUMENT_DUPLICATE"))
                .andExpect(jsonPath("$.details.documentId").value(first.path("id").asLong()));
    }

    @Test
    void scannedPdfFailsWithReadableReason() throws Exception {
        JsonNode doc = upload(new MockMultipartFile("file", "scan.pdf", "application/pdf", SampleFiles.blankPdf()),
                "Bản scan " + UUID.randomUUID());
        JsonNode done = awaitProcessed(doc.path("id").asLong());
        assertThat(done.path("status").asText()).isEqualTo("FAILED");
        assertThat(done.path("errorMessage").asText()).contains("Không trích được chữ");
    }

    @Test
    void aiOutageFailsDocumentAndReindexRecovers() throws Exception {
        embeddingModel.fail = true;
        JsonNode doc = upload(new MockMultipartFile("file", "x.txt", "text/plain",
                unique("Giao hang toan quoc trong 3 den 5 ngay lam viec").getBytes(StandardCharsets.UTF_8)), "Giao hàng");
        long id = doc.path("id").asLong();
        JsonNode failed = awaitProcessed(id);
        assertThat(failed.path("status").asText()).isEqualTo("FAILED");
        assertThat(failed.path("errorMessage").asText()).contains("OPENAI_API_KEY");

        embeddingModel.fail = false;
        mvc.perform(post(BASE + "/" + id + "/reindex").header("Authorization", admin)).andExpect(status().isOk());
        assertThat(awaitProcessed(id).path("status").asText()).isEqualTo("READY");
    }

    @Test
    void disablingReplacingAndDeletingChangeWhatTheBotReads() throws Exception {
        String marker = "ma" + UUID.randomUUID().toString().replace("-", "");
        JsonNode doc = upload(new MockMultipartFile("file", "v1.txt", "text/plain",
                ("Thoi gian mo cua cua hang " + marker + " la tu 8 gio den 21 gio hang ngay").getBytes(StandardCharsets.UTF_8)),
                "Giờ mở cửa");
        long id = doc.path("id").asLong();
        awaitProcessed(id);
        assertThat(search.search("gio mo cua " + marker)).anyMatch(h -> h.documentId() == id);

        // Tắt → không còn được truy hồi
        mvc.perform(put(BASE + "/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Giờ mở cửa\",\"isActive\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isActive").value(false));
        assertThat(search.search("gio mo cua " + marker)).noneMatch(h -> h.documentId() == id);
        mvc.perform(get(BASE + "?isActive=false").header("Authorization", admin))
                .andExpect(jsonPath("$.data[?(@.id == " + id + ")]").exists());

        // Bật lại + thay file → nội dung mới thay nội dung cũ
        mvc.perform(put(BASE + "/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Giờ mở cửa\",\"isActive\":true}")).andExpect(status().isOk());
        var replace = MockMvcRequestBuilders.multipart(BASE + "/" + id + "/file")
                .file(new MockMultipartFile("file", "v2.txt", "text/plain",
                        ("Cua hang " + marker + " nghi ngay chu nhat va mo cua tu 9 gio").getBytes(StandardCharsets.UTF_8)));
        replace.with(r -> {
            r.setMethod("PUT");
            return r;
        });
        mvc.perform(replace.header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("v2.txt"));
        JsonNode replaced = awaitProcessed(id);
        assertThat(replaced.path("chunks").get(0).path("content").asText()).contains("chu nhat");

        mvc.perform(get(BASE + "/" + id + "/file").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("v2.txt")));

        // Xoá → mất cả chunk
        mvc.perform(delete(BASE + "/" + id).header("Authorization", admin)).andExpect(status().isNoContent());
        assertThat(jdbc.sql("SELECT count(*) FROM chatbot_document_chunks WHERE document_id = ?").param(id)
                .query(Long.class).single()).isZero();
        mvc.perform(get(BASE + "/" + id).header("Authorization", admin)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void searchTestEndpointShowsHitsWithoutCallingTheChatModel() throws Exception {
        String marker = "kt" + UUID.randomUUID().toString().replace("-", "");
        JsonNode doc = upload(new MockMultipartFile("file", "faq.txt", "text/plain",
                ("Hoi dap " + marker + ": cua hang co ho tro thanh toan khi nhan hang COD").getBytes(StandardCharsets.UTF_8)),
                "Hỏi đáp");
        awaitProcessed(doc.path("id").asLong());
        mvc.perform(post(BASE + "/search-test").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"thanh toan khi nhan hang " + marker + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Hỏi đáp"))
                .andExpect(jsonPath("$.data[0].score").isNumber());
        mvc.perform(post(BASE + "/search-test").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
