package vn.campvivo.catalog.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AdminProductJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void summarySerializesIsActiveAndIntegerPrice() throws Exception {
        var summary = new AdminProductResponses.Summary(1L, "Lều", "leu", null, 2L, "Lều trại", java.util.List.of("Cắm trại", "Lều trại"),
                new BigDecimal("450000"), 5, 1, 4, true, null, OffsetDateTime.parse("2026-10-23T10:00:00+07:00"));

        JsonNode json = mapper.readTree(mapper.writeValueAsString(summary));

        assertThat(json.has("isActive")).isTrue();
        assertThat(json.has("active")).isFalse();
        assertThat(json.get("price").asText()).isEqualTo("450000");
    }

    @Test
    void requestReadsIsActiveAndIsPrimary() throws Exception {
        String body = """
                {"name":"Lều 2 người","price":450000,"categoryId":5,"isActive":false,
                 "images":[{"url":"https://img.example/a.jpg","isPrimary":true,"displayOrder":0}]}
                """;

        ProductRequest req = mapper.readValue(body, ProductRequest.class);

        assertThat(req.isActive()).isFalse();
        assertThat(req.images().get(0).isPrimary()).isTrue();
        assertThat(req.price()).isEqualByComparingTo("450000");
    }
}
