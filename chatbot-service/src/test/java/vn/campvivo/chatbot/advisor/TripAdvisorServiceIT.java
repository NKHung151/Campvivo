package vn.campvivo.chatbot.advisor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.campvivo.chatbot.advisor.AdvisorModels.PlanKind;
import vn.campvivo.chatbot.advisor.AdvisorModels.PlanLine;
import vn.campvivo.chatbot.advisor.AdvisorModels.TripAdvice;
import vn.campvivo.chatbot.advisor.AdvisorModels.TripNeed;
import vn.campvivo.chatbot.advisor.AdvisorModels.TripPlan;
import vn.campvivo.chatbot.support.IntegrationTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CB-17/CB-18: tư vấn chuyến đi trên combo + sản phẩm thật của seed V3/V4 (combo đánh giá lúc hỏi, không lưu trước).
 * Seed chỉ có lều 1 người (Coleman còn hàng, Naturehike hết) nên nhóm đông phải mua thêm nhiều lều 1 người.
 */
class TripAdvisorServiceIT extends IntegrationTest {

    @Autowired
    TripAdvisorService advisor;

    private TripAdvice advise(int people, long budget, TripType type, boolean winter, List<String> owned) {
        TripAdvice advice = advisor.advise(new TripNeed(people, budget, type, 1, winter, owned));
        advice.plans().forEach(p -> assertInvariants(p, budget));
        return advice;
    }

    /** Bất biến của mọi phương án: tiền cộng đúng, không vượt ngân sách khi đã đủ đồ, không đề xuất hàng thiếu tồn. */
    private static void assertInvariants(TripPlan plan, long budget) {
        long lines = plan.lines().stream().mapToLong(PlanLine::lineTotal).sum();
        long combo = plan.combo() == null ? 0 : plan.combo().price();
        assertThat(plan.total()).isEqualTo(combo + lines);
        assertThat(plan.remaining()).isEqualTo(budget - plan.total());
        if (plan.coversRequired()) {
            assertThat(plan.total()).isLessThanOrEqualTo(budget);
        }
        assertThat(plan.lines()).allSatisfy(l -> {
            assertThat(l.quantity()).isPositive();
            assertThat(l.product().availableQuantity()).isGreaterThanOrEqualTo(l.quantity());
            assertThat(l.lineTotal()).isEqualTo(l.product().price() * l.quantity());
        });
        if (plan.combo() != null) {
            assertThat(plan.combo().availableQuantity()).isPositive();
        }
    }

    @Test
    void soloCampingPicksTheOnePersonComboAndSpendsTheRestOnOptionalItems() {
        TripAdvice advice = advise(1, 8_000_000, TripType.CAMPING, false, List.of());

        TripPlan best = advice.plans().get(0);
        assertThat(best.kind()).isEqualTo(PlanKind.COMBO_PLUS_PRODUCTS);
        assertThat(best.combo().slug()).isEqualTo("combo-cam-trai-1-nguoi-co-ban");
        assertThat(best.coversRequired()).isTrue();
        assertThat(best.comboCovers()).contains("Lều", "Túi ngủ", "Đèn");
        assertThat(best.comboPeople()).isEqualTo(1);
        // Combo đã đủ đồ bắt buộc → chỉ mua thêm đồ "nên có"
        assertThat(best.lines()).noneMatch(l -> List.of("tent", "sleeping-bag", "light").contains(l.slotKey()));
        // Luôn có phương án mua lẻ để so sánh
        assertThat(advice.plans()).anyMatch(p -> p.kind() == PlanKind.PRODUCTS_ONLY);
    }

    @Test
    void groupCampingUsesComboAndBuysMissingTentsAndSleepingBags() {
        TripAdvice advice = advise(3, 30_000_000, TripType.CAMPING, false, List.of());

        TripPlan best = advice.plans().get(0);
        assertThat(best.combo().slug()).isEqualTo("combo-cam-trai-1-nguoi-co-ban");
        assertThat(best.coversRequired()).isTrue();
        PlanLine tents = best.lines().stream().filter(l -> l.slotKey().equals("tent")).findFirst().orElseThrow();
        assertThat(tents.quantity()).isEqualTo(2);              // combo có lều 1 người, còn 2 người
        assertThat(tents.product().name()).contains("1 người");
        PlanLine bags = best.lines().stream().filter(l -> l.slotKey().equals("sleeping-bag")).findFirst().orElseThrow();
        assertThat(bags.quantity()).isEqualTo(2);
        assertThat(bags.reason()).contains("Combo đã có 1");
        // Seed V3 có "Giày leo núi ... 3A024A" bị gắn nhầm danh mục Túi ngủ — không được đề xuất làm túi ngủ
        assertThat(bags.product().name()).containsIgnoringCase("túi ngủ");
    }

    @Test
    void tooSmallBudgetReportsMinimumNeeded() {
        TripAdvice advice = advise(2, 3_000_000, TripType.CAMPING, false, List.of());

        assertThat(advice.plans()).noneMatch(TripPlan::coversRequired);
        assertThat(advice.minimumBudget()).isNotNull().isGreaterThan(3_000_000L);
        assertThat(advice.plans().get(0).missingRequired()).isNotEmpty();
    }

    @Test
    void ownedItemsAreSkipped() {
        TripAdvice advice = advise(3, 30_000_000, TripType.CAMPING, false, List.of("lều", "túi ngủ"));

        assertThat(advice.ignoredBecauseOwned()).containsExactlyInAnyOrder("Lều", "Túi ngủ");
        assertThat(advice.plans()).allSatisfy(p -> assertThat(p.lines())
                .noneMatch(l -> l.slotKey().equals("tent") || l.slotKey().equals("sleeping-bag")));
    }

    @Test
    void dayHikeMatchesTrekkingCombo() {
        TripPlan best = advise(1, 2_000_000, TripType.TREKKING_DAY, false, List.of()).plans().get(0);
        assertThat(best.combo().slug()).isEqualTo("combo-leo-nui-trong-ngay");
        assertThat(best.comboCovers()).contains("Balo", "Gậy leo núi");
        assertThat(best.coversRequired()).isTrue();
    }

    @Test
    void beachTripMatchesSwimCombo() {
        TripPlan best = advise(1, 1_000_000, TripType.BEACH, false, List.of()).plans().get(0);
        assertThat(best.combo().slug()).isEqualTo("combo-boi-cho-be");
    }

    @Test
    void soldOutCombosAreNeverSuggestedAndWinterPrefersWinterSleepingBags() {
        TripAdvice advice = advise(2, 40_000_000, TripType.CAMPING, true, List.of());

        assertThat(advice.plans()).noneMatch(p -> p.combo() != null
                && p.combo().slug().equals("combo-ngu-am-mua-dong"));   // seed: 0 bộ
        assertThat(advice.plans().get(0).lines()).filteredOn(l -> l.slotKey().equals("sleeping-bag"))
                .allSatisfy(l -> assertThat(l.product().categorySlug()).contains("mua-dong"));
    }

    @Test
    void comboEditedInDbIsReflectedOnNextQuestion() {
        long comboId = jdbc.sql("SELECT id FROM product_combos WHERE slug = 'combo-leo-nui-trong-ngay'")
                .query(Long.class).single();
        jdbc.sql("UPDATE product_combos SET is_active = false WHERE id = ?").param(comboId).update();
        try {
            TripAdvice advice = advise(1, 2_000_000, TripType.TREKKING_DAY, false, List.of());
            assertThat(advice.plans()).noneMatch(p -> p.combo() != null && p.combo().id() == comboId);
        } finally {
            jdbc.sql("UPDATE product_combos SET is_active = true WHERE id = ?").param(comboId).update();
        }
    }
}
