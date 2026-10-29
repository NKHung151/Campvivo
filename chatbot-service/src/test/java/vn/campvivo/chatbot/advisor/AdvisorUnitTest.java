package vn.campvivo.chatbot.advisor;

import org.junit.jupiter.api.Test;
import vn.campvivo.chatbot.advisor.TripTemplateProperties.QuantityMode;
import vn.campvivo.chatbot.advisor.TripTemplateProperties.Slot;
import vn.campvivo.chatbot.catalog.CatalogModels.CategoryNode;
import vn.campvivo.chatbot.catalog.CatalogModels.Spec;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phần thuần Java của tư vấn: đọc sức chứa lều, số lượng cần theo slot, nhận "đã có ..." không dấu.
 */
class AdvisorUnitTest {

    @Test
    void readsTentCapacityFromNameOrSpecs() {
        assertThat(CapacityParser.capacity("Lều 4 người Naturehike", List.of())).hasValue(4);
        assertThat(CapacityParser.capacity("Lều cắm trại 2-3 người", List.of())).hasValue(3);
        assertThat(CapacityParser.capacity("Lều 1 nguoi", List.of())).hasValue(1);
        assertThat(CapacityParser.capacity("Lều dã ngoại", List.of(new Spec("Sức chứa", "6 người")))).hasValue(6);
        assertThat(CapacityParser.capacity("Lều dã ngoại", List.of(new Spec("Số người", "2")))).hasValue(2);
        assertThat(CapacityParser.capacity("Lều dã ngoại", List.of(new Spec("Chất liệu", "Polyester 210T"))))
                .isEmpty();
    }

    @Test
    void computesUnitsNeededPerSlot() {
        assertThat(slot(QuantityMode.PER_PERSON, 1, 4).needFor(3)).isEqualTo(3);
        assertThat(slot(QuantityMode.PER_PERSON, 2, 4).needFor(3)).isEqualTo(6);     // gậy: 2 cây/người
        assertThat(slot(QuantityMode.PER_GROUP, 1, 4).needFor(5)).isEqualTo(2);      // 1 cái / 4 người
        assertThat(slot(QuantityMode.CAPACITY, 1, 4).needFor(5)).isEqualTo(5);
    }

    @Test
    void recognisesOwnedItemsWithoutDiacritics() {
        Slot tent = new Slot("tent", "Lều", List.of("leu-trai"), List.of(), List.of(), true, QuantityMode.CAPACITY,
                1, 4, 30, List.of("lều", "tent"), List.of("lều", "tent"));
        ResolvedSlot resolved = ResolvedSlot.of(tent,
                Map.of("leu-trai", new CategoryNode(179, 58L, "Lều trại", "leu-trai", "/9/58/179/")));
        assertThat(resolved.mentionedIn("nhà mình có LEU rồi")).isTrue();
        assertThat(resolved.mentionedIn("Đã có lều")).isTrue();
        assertThat(resolved.mentionedIn("đã có bếp")).isFalse();
        assertThat(resolved.matches("/9/58/179/")).isTrue();
        assertThat(resolved.matches("/9/58/180/")).isFalse();
        // Chống hàng gắn nhầm danh mục: chỉ nhận sản phẩm lẻ có từ khoá trong tên
        assertThat(resolved.acceptsName("Lều 4 người Naturehike")).isTrue();
        assertThat(resolved.acceptsName("LEU cam trai")).isTrue();
        assertThat(resolved.acceptsName("Giày leo núi cổ cao Clorts")).isFalse();
    }

    @Test
    void estimatesPeopleFromPerPersonAndCapacitySlots() {
        Slot tent = new Slot("tent", "Lều", List.of(), List.of(), List.of(), true, QuantityMode.CAPACITY, 1, 4, 30,
                List.of(), List.of());
        Slot bag = new Slot("bag", "Túi ngủ", List.of(), List.of(), List.of(), true, QuantityMode.PER_PERSON, 1, 4,
                20, List.of(), List.of());
        Slot stove = new Slot("stove", "Bếp", List.of(), List.of(), List.of(), false, QuantityMode.PER_GROUP, 1, 4,
                10, List.of(), List.of());
        List<ResolvedSlot> slots = List.of(ResolvedSlot.of(tent, Map.of()), ResolvedSlot.of(bag, Map.of()),
                ResolvedSlot.of(stove, Map.of()));
        assertThat(ComboFitAnalyzer.estimatePeople(slots, Map.of("tent", 4, "bag", 2, "stove", 1))).isEqualTo(2);
        assertThat(ComboFitAnalyzer.estimatePeople(slots, Map.of("stove", 1))).isNull();
    }

    private static Slot slot(QuantityMode mode, int amount, int groupSize) {
        return new Slot("k", "Món", List.of(), List.of(), List.of(), true, mode, amount, groupSize, 10, List.of(),
                List.of());
    }
}
