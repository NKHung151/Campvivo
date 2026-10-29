package vn.campvivo.chatbot.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import vn.campvivo.chatbot.support.IntegrationTest;
import vn.campvivo.chatbot.tools.AdvisorTools.AdviceView;
import vn.campvivo.chatbot.tools.CatalogTools.CategoryView;
import vn.campvivo.chatbot.tools.CatalogTools.SearchResult;
import vn.campvivo.chatbot.tools.ToolViews.Notice;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CB-12/CB-19: tool trả JSON gọn từ DB, gom thẻ đúng, lỗi tham số trả thông báo cho model thay vì ném exception,
 * giới hạn số lượt gọi.
 */
class ToolsIT extends IntegrationTest {

    @Autowired
    CatalogTools catalogTools;
    @Autowired
    AdvisorTools advisorTools;

    private static ToolContext context(ToolSession session) {
        return new ToolContext(Map.of(ToolSession.CONTEXT_KEY, session));
    }

    @Test
    void searchProductsFiltersByPriceAndCollectsCards() {
        ToolSession session = new ToolSession(5);
        Object result = catalogTools.searchProducts("lều", "leu-trai", null, null, 5_000_000L, "PRICE_ASC", false, 8,
                context(session));

        assertThat(result).isInstanceOf(SearchResult.class);
        SearchResult r = (SearchResult) result;
        assertThat(r.products()).isNotEmpty().allSatisfy(p -> {
            assertThat(p.price()).isLessThanOrEqualTo(5_000_000L);
            assertThat(p.priceText()).endsWith("đ");
            assertThat(p.availableQuantity()).isPositive();
        });
        // Không nhắc tên sản phẩm nào → vẫn có vài thẻ tìm được
        assertThat(session.cards("Mình tìm được vài mẫu cho bạn.")).isNotEmpty();
        // Có nhắc tên → chỉ thẻ được nhắc
        String first = r.products().get(0).name();
        assertThat(session.cards("Bạn tham khảo **" + first + "** nhé")).extracting(ChatCard::name)
                .containsExactly(first);
    }

    @Test
    void unknownCategoryAndSlugGiveNoticesNotExceptions() {
        ToolSession session = new ToolSession(5);
        SearchResult r = (SearchResult) catalogTools.searchProducts(null, "khong-co", null, null, null, null, null,
                null, context(session));
        assertThat(r.count()).isZero();
        assertThat(r.notice()).contains("listCategories");
        assertThat(catalogTools.getProductDetail("khong-co-san-pham", context(session))).isInstanceOf(Notice.class);
    }

    @Test
    void listCategoriesReturnsRootsThenChildren() {
        ToolSession session = new ToolSession(5);
        @SuppressWarnings("unchecked")
        List<CategoryView> roots = (List<CategoryView>) catalogTools.listCategories(null, context(session));
        assertThat(roots).extracting(CategoryView::slug).contains("do-cam-trai", "do-leo-nui");
        @SuppressWarnings("unchecked")
        List<CategoryView> children = (List<CategoryView>) catalogTools.listCategories("do-cam-trai", context(session));
        assertThat(children).extracting(CategoryView::slug).contains("leu-phu-kien-leu", "tui-ngu");
    }

    @Test
    void stopsAfterMaxToolCalls() {
        ToolSession session = new ToolSession(2);
        catalogTools.listCategories(null, context(session));
        catalogTools.listCategories(null, context(session));
        Object third = catalogTools.listCategories(null, context(session));
        assertThat(third).isInstanceOf(Notice.class);
        assertThat(((Notice) third).notice()).contains("quá nhiều lần");
    }

    @Test
    void recommendForTripValidatesInputAndRecommendsComboCards() {
        ToolSession session = new ToolSession(5);
        assertThat(advisorTools.recommendForTrip(0, 5_000_000L, "CAMPING", 1, false, null, context(session)))
                .isInstanceOf(Notice.class);
        assertThat(advisorTools.recommendForTrip(2, null, "CAMPING", 1, false, null, context(session)))
                .isInstanceOf(Notice.class);
        assertThat(advisorTools.recommendForTrip(2, 5_000_000L, "DI_CHOI", 1, false, null, context(session)))
                .isInstanceOf(Notice.class);

        AdviceView advice = (AdviceView) advisorTools.recommendForTrip(1, 8_000_000L, "camping", 1, false, null,
                context(session));
        assertThat(advice.plans().get(0).comboSlug()).isEqualTo("combo-cam-trai-1-nguoi-co-ban");
        assertThat(advice.plans().get(0).totalText()).endsWith("đ");
        List<ChatCard> cards = session.cards("Combo phù hợp nhất là ...");
        assertThat(cards.get(0).type()).isEqualTo("COMBO");
        assertThat(cards.get(0).suggestedQuantity()).isEqualTo(1);
        assertThat(cards.get(0).note()).contains("lều");
    }

    @Test
    void dayHikeWithNightsBecomesOvernightTrek() {
        assertThat(AdvisorTools.parseType("TREKKING_DAY", 2)).isEqualTo(vn.campvivo.chatbot.advisor.TripType.TREKKING_OVERNIGHT);
        assertThat(AdvisorTools.parseType("TREKKING_DAY", 0)).isEqualTo(vn.campvivo.chatbot.advisor.TripType.TREKKING_DAY);
    }
}
