package vn.campvivo.chatbot.common;

import org.junit.jupiter.api.Test;
import vn.campvivo.chatbot.common.util.PgVector;
import vn.campvivo.chatbot.common.util.TextUtil;
import vn.campvivo.chatbot.knowledge.DocumentType;
import vn.campvivo.chatbot.knowledge.TextCleaner;
import vn.campvivo.chatbot.tools.ChatCard;
import vn.campvivo.chatbot.tools.ToolSession;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HelpersTest {

    @Test
    void formatsMoneyAndStripsHtml() {
        assertThat(TextUtil.formatVnd(1_250_000)).isEqualTo("1.250.000đ");
        assertThat(TextUtil.htmlToText("<p>Lều <strong>chống mưa</strong></p>\n<ul><li>2 người</li></ul>"))
                .isEqualTo("Lều chống mưa 2 người");
        assertThat(TextUtil.truncate("abcdef", 4)).isEqualTo("abc…");
        assertThat(PgVector.toLiteral(new float[]{0.5f, -1f})).isEqualTo("[0.5,-1.0]");
    }

    @Test
    void detectsDocumentTypeByExtensionAndSignature() {
        assertThat(DocumentType.detect("a.PDF", "%PDF-1.7".getBytes())).contains(DocumentType.PDF);
        assertThat(DocumentType.detect("a.pdf", "hello".getBytes())).isEmpty();
        assertThat(DocumentType.detect("a.docx", new byte[]{'P', 'K', 3, 4, 0})).contains(DocumentType.DOCX);
        assertThat(DocumentType.detect("a.doc", new byte[]{1})).isEmpty();
        assertThat(DocumentType.detect("ghi-chu.md", "# x".getBytes(StandardCharsets.UTF_8))).contains(DocumentType.MD);
    }

    @Test
    void cleansExtractedText() {
        assertThat(TextCleaner.clean("  Dòng 1 \r\n\r\n\r\n\r\nDòng\t\t2\u0000 ")).isEqualTo("Dòng 1\n\nDòng 2");
    }

    @Test
    void cardsPreferRecommendedThenMentionedSearchResults() {
        ToolSession s = new ToolSession(5);
        ChatCard tent = new ChatCard("PRODUCT", 1, "leu", "Lều 1 người Coleman Instant up Dome S", 3_999_000, null, 3,
                null, null);
        ChatCard bag = new ChatCard("PRODUCT", 2, "tui", "Túi ngủ nỉ Northpeak", 500_000, null, 3, null, null);
        ChatCard combo = new ChatCard("COMBO", 9, "combo", "Combo cắm trại", 5_490_000, null, 1, 1, "Combo đã gồm lều");
        s.found(tent);
        s.found(bag);
        s.recommend(combo);

        assertThat(s.cards("Bạn xem **Lều 1 người Coleman Instant up Dome S** nhé"))
                .extracting(ChatCard::slug).containsExactly("combo", "leu");
        assertThat(s.cards("Không nhắc tên nào")).extracting(ChatCard::slug).containsExactly("combo");
        // Tìm lại thẻ đã đề xuất không làm mất trạng thái đề xuất
        s.found(combo);
        assertThat(s.cards("")).extracting(ChatCard::slug).containsExactly("combo");
    }
}
